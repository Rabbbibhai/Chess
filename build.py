import re, itertools
from xml.sax.saxutils import escape
import os
HERE = os.path.dirname(os.path.abspath(__file__))
def read(p): return open(os.path.join(HERE, 'src', p), encoding='utf-8').read()
bodies = [read('Game.server.lua'), read('Admin.server.lua'), read('GunClient.client.lua')]

n = itertools.count()
def ref(): return f"RBX{next(n)}"
def col(r,g,b): return (0xFF<<24)|(r<<16)|(g<<8)|b
def cf(x,y,z):
    return f'<CoordinateFrame name="CFrame"><X>{x}</X><Y>{y}</Y><Z>{z}</Z><R00>1</R00><R01>0</R01><R02>0</R02><R10>0</R10><R11>1</R11><R12>0</R12><R20>0</R20><R21>0</R21><R22>1</R22></CoordinateFrame>'
def part(cls, name, pos, size, color, material=256, extra=""):
    return f'''<Item class="{cls}" referent="{ref()}"><Properties>
<string name="Name">{name}</string>
<bool name="Anchored">true</bool>
{cf(*pos)}
<Vector3 name="size"><X>{size[0]}</X><Y>{size[1]}</Y><Z>{size[2]}</Z></Vector3>
<Color3uint8 name="Color3uint8">{col(*color)}</Color3uint8>
<token name="Material">{material}</token>
{extra}</Properties></Item>'''
def script(cls, name, code):
    return f'''<Item class="{cls}" referent="{ref()}"><Properties>
<string name="Name">{name}</string>
<ProtectedString name="Source"><![CDATA[{code}]]></ProtectedString>
</Properties></Item>'''
def folder(name, children):
    return f'<Item class="Folder" referent="{ref()}"><Properties><string name="Name">{name}</string></Properties>{"".join(children)}</Item>'

# ---- course ----
checkpoints, platforms = [], []
x_pat = [0, 3, -3, 0, 4, -4]
z = 0
spawn = part("SpawnLocation", "Spawn", (0,20,0), (12,1,12), (120,120,130),
             extra='<bool name="Neutral">true</bool><int name="Duration">0</int>')
z = 9
cp_num = 0
for stage in range(3):
    for i in range(6):
        z += 9
        platforms.append(part("Part", f"Jump{stage}_{i}", (x_pat[i], 20 + stage*3 + i*0.5, z), (5,1,5), (90,160,255)))
    z += 10
    cp_num += 1
    checkpoints.append(part("Part", str(cp_num), (0, 20 + stage*3 + 3.5, z), (12,1,12), (80,220,120)))
# finish pad
z += 12
platforms.append(part("Part", "Finish", (0, 31, z), (16,1,16), (255,215,0)))

lava = part("Part", "Lava", (0,0,100), (500,1,500), (255,90,20), material=288)

ws = f'''<Item class="Workspace" referent="{ref()}"><Properties><string name="Name">Workspace</string></Properties>
{spawn}
{folder("Checkpoints", checkpoints)}
{folder("KillBricks", [lava])}
{folder("Platforms", platforms)}
</Item>'''

sss = f'''<Item class="ServerScriptService" referent="{ref()}"><Properties><string name="Name">ServerScriptService</string></Properties>
{script("Script","Game",bodies[0])}
{script("Script","Admin",bodies[1])}
</Item>'''
sp = f'''<Item class="StarterPlayer" referent="{ref()}"><Properties><string name="Name">StarterPlayer</string></Properties>
<Item class="StarterPlayerScripts" referent="{ref()}"><Properties><string name="Name">StarterPlayerScripts</string></Properties>
{script("LocalScript","GunClient",bodies[2])}
</Item></Item>'''

xml = f'''<roblox xmlns:xmime="http://www.w3.org/2005/05/xmlmime" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="http://www.roblox.com/roblox.xsd" version="4">
<External>null</External><External>nil</External>
{ws}
{sss}
{sp}
</roblox>'''
open(os.path.join(HERE,'ParkourGame.rbxlx'),'w').write(xml)
import xml.dom.minidom as m; m.parseString(xml); print("ok", len(xml), [len(b) for b in bodies])
