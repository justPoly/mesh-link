from pathlib import Path
import uuid

def load_identity():
    p=Path(__file__).with_name('node_id.txt')
    if p.exists(): return str(uuid.UUID(p.read_text().strip()))
    node=str(uuid.uuid4())
    p.write_text(node+'\n',encoding='ascii')
    return node
