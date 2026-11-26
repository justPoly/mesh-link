import base64
from dataclasses import dataclass

@dataclass(frozen=True)
class Probe:
    kind: str
    node: str
    sequence: int
    timestamp: int
    internet: bool = False
    name: str = 'Unknown device'
    port: int = 9999

    def encode(self):
        return '|'.join((self.kind,self.node,str(self.sequence),str(self.timestamp),str(int(self.internet)),base64.b64encode(self.name.encode()).decode(),str(self.port))).encode()

    @classmethod
    def decode(cls,data):
        p=data.decode().split('|')
        if len(p) not in (5,7) or p[0] not in ('REQUEST','RESPONSE') or not p[1].strip() or p[4] not in ('0','1'):
            raise ValueError('Invalid probe')
        seq,ts=map(int,p[2:4])
        if not all(-2**63 <= n < 2**63 for n in (seq,ts)):
            raise ValueError('Invalid long')
        name=base64.b64decode(p[5],validate=True).decode() if len(p)==7 else 'Unknown device'
        port=int(p[6]) if len(p)==7 else 9999
        if not 1<=port<=65535: raise ValueError('Invalid port')
        return cls(p[0],p[1],seq,ts,p[4]=='1',name if name.strip() else 'Unknown device',port)
