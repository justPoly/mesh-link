# Mesh-Link desktop diagnostic node
Python 3.10+. No external dependencies. Extract this folder under tools/desktop-test-node.
Run `python -m unittest -v` then `python main.py --bind 172.22.186.169 --peer 172.22.186.65 --name "Speed 2c"`.
Use current IPs from ipconfig if changed. Keep Android app open. Commands: peers, test, pause, resume, stats, exit.
This uses explicit unicast on the selected local IPv4, not Wi-Fi Direct group management or broadcast discovery. No NAT, forwarding, encryption or internet gateway capability. Only diagnostic RTT packets are handled. The protocol is unauthenticated; use only a controlled test network. Pause drops incoming traffic and stops probes, but does not disconnect the interface. Restart preserves node_id.txt. Codec tests use fixed vectors and round trips; actual Android interoperability is not yet verified.
