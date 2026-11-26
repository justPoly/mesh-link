import argparse, socket, select, threading, queue, time, itertools, ipaddress
from dataclasses import dataclass
from node_identity import load_identity
from probe_codec import Probe
from mesh_packet import MeshPacket, PacketType
from config import *

@dataclass
class Peer:
    name: str
    endpoint: tuple
    mesh_port: int
    last: float=0
    state: str='DISCOVERED'


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--bind',required=True,help='Local laptop IPv4')
    parser.add_argument('--peer',required=True,help='Android IPv4')
    parser.add_argument('--name',default='Mesh-Link Desktop')
    a=parser.parse_args()
    for ip in (a.bind,a.peer): ipaddress.IPv4Address(ip)
    if a.bind==a.peer or a.bind=='0.0.0.0': parser.error('Use distinct explicit interface and peer IPs')
    node=load_identity(); peers={}; pending={}; tests={}; seq=itertools.count(); commands=queue.Queue(); paused=False; sent=passed=failed=0
    def log(s): print(time.strftime('%H:%M:%S'),s,flush=True)
    def sock(port):
        s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM); s.bind((a.bind,port)); return s
    def console():
        while True:
            try: commands.put(input().strip().lower())
            except EOFError: return
    with sock(DISCOVERY_PORT) as discovery, sock(MESH_PORT) as mesh:
        def probe():
            n=next(seq); now=time.monotonic(); ts=int(time.time()*1000)
            discovery.sendto(Probe('REQUEST',node,n,ts,False,a.name,MESH_PORT).encode(),(a.peer,DISCOVERY_PORT)); pending[n]=(now,ts)
        def send_mesh(p,peer): mesh.sendto(p.encode(),(peer.endpoint[0],peer.mesh_port))
        def test():
            nonlocal sent
            for nid,p in peers.items():
                if p.state!='CONNECTED' or any(t[0]==nid for t in tests.values()): continue
                packet=MeshPacket(node,nid,PacketType.RTT_PROBE)
                send_mesh(packet,p); tests[packet.packet_id]=(nid,time.monotonic(),(p.endpoint[0],p.mesh_port)); sent+=1
                log('MESH_TEST_STARTED '+nid)
        log(f'Node {node} | bind {a.bind} | peer {a.peer} | Internet advertised: false')
        log('Commands: peers, test, pause, resume, stats, exit')
        threading.Thread(target=console,daemon=True).start(); next_probe=0
        while True:
            now=time.monotonic()
            while not commands.empty():
                c=commands.get()
                if c=='exit': return
                if c=='pause': paused=True; pending.clear(); log('PAUSED')
                elif c=='resume': paused=False; next_probe=0; log('RESUMED')
                elif c=='peers':
                    for nid,p in peers.items(): log(f'{p.state} {p.name!r} {nid} {p.endpoint[0]}:{p.mesh_port}')
                    if not peers: log('No peers')
                elif c=='stats': log(f'Tests sent={sent} passed={passed} failed={failed} pending={len(tests)}')
                elif c=='test' and not paused: test()
                elif c not in ('pause','resume','test',''): log('Unknown command')
            if not paused and now>=next_probe:
                probe(); next_probe=now+HEARTBEAT
            for n,(t,_) in list(pending.items()):
                if now-t>=10: del pending[n]
            for nid,p in peers.items():
                if p.state!='LOST' and now-p.last>=PEER_TIMEOUT: p.state='LOST'; log('PEER_LOST '+nid)
            for pid,(nid,t,ep) in list(tests.items()):
                if now-t>=TEST_TIMEOUT: del tests[pid]; failed+=1; log('MESH_TEST_TIMEOUT '+nid)
            ready,_,_=select.select([discovery,mesh],[],[],0.2)
            for s in ready:
                try: data,addr=s.recvfrom(65535)
                except ConnectionResetError: continue
                if paused or addr[0]!=a.peer: continue
                try:
                    if s is discovery:
                        if addr[1]!=DISCOVERY_PORT or len(data)>2048: continue
                        m=Probe.decode(data)
                        if m.node==node: continue
                        now=time.monotonic()
                        if m.kind=='RESPONSE':
                            entry=pending.get(m.sequence)
                            if not entry or now-entry[0]>=10 or entry[1]!=m.timestamp: continue
                            del pending[m.sequence]
                        p=peers.get(m.node)
                        if p is None:
                            p=Peer(m.name,addr,m.port,now); peers[m.node]=p; log('PEER_DISCOVERED '+repr(m.name))
                        if p.endpoint!=addr or p.mesh_port!=m.port: p.state='DISCOVERED'
                        p.name,p.endpoint,p.mesh_port=m.name,addr,m.port
                        if m.kind=='REQUEST':
                            discovery.sendto(Probe('RESPONSE',node,m.sequence,m.timestamp,False,a.name,MESH_PORT).encode(),addr)
                            if p.state!='CONNECTED': next_probe=0
                        else:
                            if p.state!='CONNECTED': log('PEER_CONNECTED '+m.node)
                            p.state='CONNECTED'; p.last=now; log(f'HEARTBEAT RTT={(now-entry[0])*1000:.1f}ms')
                    else:
                        m=MeshPacket.decode(data); p=peers.get(m.source)
                        if not p or p.state!='CONNECTED' or addr!=(p.endpoint[0],p.mesh_port) or m.destination!=node or m.ttl!=1 or m.encrypted or m.payload: continue
                        if m.kind==PacketType.RTT_PROBE:
                            send_mesh(MeshPacket(node,m.source,PacketType.RTT_RESPONSE,ack=m.packet_id),p); log('MESH_PROBE_RECEIVED / RESPONSE_SENT '+m.source)
                        elif m.kind==PacketType.RTT_RESPONSE:
                            t=tests.get(m.ack)
                            if t and t[0]==m.source and t[2]==addr and time.monotonic()-t[1]<TEST_TIMEOUT:
                                del tests[m.ack]; passed+=1; log(f'MESH_TEST_PASSED RTT={(time.monotonic()-t[1])*1000:.1f}ms')
                except (ValueError,UnicodeError) as e: log('Rejected packet: '+str(e))

if __name__=='__main__':
    try: main()
    except KeyboardInterrupt: print('\nStopped')
    except OSError as e: raise SystemExit(f'Network error: {e}')
