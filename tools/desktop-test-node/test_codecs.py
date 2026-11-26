import unittest
from probe_codec import Probe
from mesh_packet import MeshPacket, PacketType, utf
class Tests(unittest.TestCase):
    def test_probe(self):
        p=Probe('REQUEST','abc',1,2,False,'Speed 2c')
        self.assertEqual(p.encode(),b'REQUEST|abc|1|2|0|U3BlZWQgMmM=|9999')
        self.assertEqual(Probe.decode(p.encode()),p)
    def test_legacy(self): self.assertEqual(Probe.decode(b'RESPONSE|abc|1|2|1').port,9999)
    def test_utf(self): self.assertEqual(utf('\0😀').hex(),'0008c080eda0bdedb880')
    def test_packet(self):
        p=MeshPacket('a','b',PacketType.RTT_RESPONSE,packet_id='c',ack='d')
        expected=bytes.fromhex('4d53484c01 000163 000161 01 000162 00000001 0000000000000000 00 00 01 000164 02 00000000')
        self.assertEqual(p.encode(),expected); self.assertEqual(MeshPacket.decode(expected),p)
    def test_invalid(self):
        with self.assertRaises(ValueError): MeshPacket.decode(b'MSHL\x01')
if __name__=='__main__': unittest.main()
