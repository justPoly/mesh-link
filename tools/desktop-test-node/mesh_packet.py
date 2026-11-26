import struct, io, uuid
from dataclasses import dataclass, field
from enum import IntEnum

class PacketType(IntEnum):
    HELLO=0; RTT_PROBE=1; RTT_RESPONSE=2; GATEWAY_ANNOUNCE=3; NAT_FORWARD=4; DATA=5; ACK=6; KEY_EXCHANGE=7

def utf(s):
    if len(s.encode('utf-8'))>4096: raise ValueError('String too long')
    units=s.encode('utf-16-be')
    b=bytearray()
    for i in range(0,len(units),2):
        n=int.from_bytes(units[i:i+2],'big')
        if 1<=n<=127: b.append(n)
        elif n<=2047: b.extend((0xc0|(n>>6),0x80|(n&63)))
        else: b.extend((0xe0|(n>>12),0x80|((n>>6)&63),0x80|(n&63)))
    return struct.pack('>H',len(b))+b

def nullable(s): return b'\0' if s is None else b'\1'+utf(s)

class Reader:
    def __init__(self,b): self.f=io.BytesIO(b)
    def read(self,n):
        b=self.f.read(n)
        if len(b)!=n: raise ValueError('Truncated packet')
        return b
    def num(self,fmt): return struct.unpack('>'+fmt,self.read(struct.calcsize('>'+fmt)))[0]
    def string(self):
        b=self.read(self.num('H')); i=0; out=bytearray()
        while i<len(b):
            x=b[i]; i+=1
            if x<128: n=x
            elif 0xc0<=x<=0xdf:
                if i>=len(b) or b[i]&0xc0!=0x80: raise ValueError('Bad UTF')
                n=((x&31)<<6)|(b[i]&63); i+=1
            elif 0xe0<=x<=0xef:
                if i+1>=len(b) or any(y&0xc0!=0x80 for y in b[i:i+2]): raise ValueError('Bad UTF')
                n=((x&15)<<12)|((b[i]&63)<<6)|(b[i+1]&63); i+=2
            else: raise ValueError('Bad UTF')
            out.extend(n.to_bytes(2,'big'))
        s=out.decode('utf-16-be')
        if len(s.encode())>4096: raise ValueError('String too long')
        return s
    def nullable(self): return self.string() if self.num('B') else None

@dataclass
class MeshPacket:
    source: str
    destination: str|None
    kind: PacketType
    packet_id: str=field(default_factory=lambda:str(uuid.uuid4()))
    ttl: int=1
    sequence: int=0
    requires_ack: bool=False
    encrypted: bool=False
    ack: str|None=None
    payload: bytes=b''
    def encode(self):
        b=b'MSHL\x01'+utf(self.packet_id)+utf(self.source)+nullable(self.destination)+struct.pack('>iq??',self.ttl,self.sequence,self.requires_ack,self.encrypted)+nullable(self.ack)+struct.pack('>Bi',self.kind,len(self.payload))+self.payload
        if len(b)>65507 or not 0<=self.ttl<=255: raise ValueError('Invalid packet size or TTL')
        return b
    @classmethod
    def decode(cls,b):
        if len(b)>65507: raise ValueError('Oversized packet')
        r=Reader(b)
        if r.read(5)!=b'MSHL\x01': raise ValueError('Invalid header')
        pid,src,dst=r.string(),r.string(),r.nullable()
        ttl,seq,req,enc=r.num('i'),r.num('q'),r.num('?'),r.num('?')
        ack=r.nullable(); kind=PacketType(r.num('B')); n=r.num('i')
        if n<0 or not 0<=ttl<=255: raise ValueError('Invalid length or TTL')
        payload=r.read(n)
        if r.f.read(1): raise ValueError('Trailing bytes')
        return cls(src,dst,kind,pid,ttl,seq,req,enc,ack,payload)
