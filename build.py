import itertools, os
HERE = os.path.dirname(os.path.abspath(__file__))
def read(p): return open(os.path.join(HERE, 'src', p), encoding='utf-8').read()

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

NEON = 288
F = {k: [] for k in ["Checkpoints","KillBricks","Platforms","Movers","Spinners","Vanish","Bouncers","Conveyors"]}
uid = itertools.count(1)
def add(fold, name, pos, size, color, mat=256):
    F[fold].append(part("Part", name, pos, size, color, mat))
def U(): return next(uid)

PAD=(45,45,70); GREEN=(60,255,120); YEL=(255,220,0); PINK=(255,60,200)
CYAN=(0,240,255); RED=(255,40,40); ORANGE=(255,140,0); GOLD=(255,215,0)

y = 20
spawn = part("SpawnLocation","Spawn",(0,y,0),(14,1,14),(120,120,130),
             extra='<bool name="Neutral">true</bool><int name="Duration">0</int>')
def cp(num, z, y): add("Checkpoints", str(num), (0,y,z), (12,1,12), GREEN, NEON)

# Stage 1: bounce pads
for z in (18,33,48,63,78): add("Bouncers", f"Bounce:100:{U()}", (0,y,z), (6,1,6), YEL, NEON)
cp(1, 93, y)
# Stage 2: moving platforms
for z in (108,120,132,144): add("Movers", f"Mover:x:16:2.5:{U()}", (-8,y,z), (6,1,6), PINK, NEON)
add("Movers", f"Mover:y:12:2.5:{U()}", (0,y,157), (6,1,6), PINK, NEON)
y = 32
cp(2, 172, y)
# Stage 3: vanishing tiles
for i,z in enumerate(range(188,260,10)):
    add("Vanish", f"Vanish:{U()}", ((0,4,-4)[i%3],y,z), (5,1,5), CYAN, NEON)
cp(3, 272, y)
# Stage 4: spinners
add("Platforms","PadA",(0,y,296),(24,1,24),PAD)
add("Spinners",f"Spinner:70:{U()}",(0,y+2,296),(22,1.5,1.5),RED,NEON)
add("Platforms","Bridge",(0,y,313),(5,1,10),PAD)
add("Platforms","PadB",(0,y,330),(24,1,24),PAD)
add("Spinners",f"Spinner:-100:{U()}",(0,y+2,330),(22,1.5,1.5),RED,NEON)
add("Platforms","PadC",(0,y,362),(24,1,24),PAD)
add("Spinners",f"Spinner:140:{U()}",(0,y+2,362),(22,1.5,1.5),RED,NEON)
cp(4, 386, y)
# Stage 5: conveyors + laser walls
for z,s in ((410,20),(432,-20),(454,20)):
    add("Conveyors", f"Conveyor:x:{s}:{U()}", (0,y,z), (10,1,20), ORANGE, NEON)
add("Platforms","LaserPad",(0,y,492),(16,1,44),PAD)
for z in (480,492,504):
    add("Movers", f"KMover:z:10:1.4:{U()}", (0,y+3,z), (16,5,1), RED, NEON)
cp(5, 520, y)
# Stage 6: rising bounce finale
for z,yy in ((536,32),(554,38),(572,44),(590,50)):
    add("Bouncers", f"Bounce:120:{U()}", (0,yy,z), (6,1,6), YEL, NEON)
finish = part("Part","Finish",(0,56,612),(20,1,20),GOLD,NEON)

F["KillBricks"].append(part("Part","Lava",(0,0,300),(400,1,800),(255,90,20),NEON))

ws = f'''<Item class="Workspace" referent="{ref()}"><Properties><string name="Name">Workspace</string></Properties>
{spawn}
{finish}
{"".join(folder(k, v) for k, v in F.items())}
</Item>'''

lighting = f'''<Item class="Lighting" referent="{ref()}"><Properties><string name="Name">Lighting</string>
<string name="TimeOfDay">00:00:00</string>
<float name="Brightness">1.5</float>
<Color3 name="Ambient"><R>0.25</R><G>0.1</G><B>0.4</B></Color3>
<Color3 name="OutdoorAmbient"><R>0.3</R><G>0.15</G><B>0.5</B></Color3>
</Properties>
<Item class="BloomEffect" referent="{ref()}"><Properties><string name="Name">Bloom</string><float name="Intensity">1.2</float><int name="Size">32</int><float name="Threshold">0.8</float></Properties></Item>
<Item class="Atmosphere" referent="{ref()}"><Properties><string name="Name">Atmosphere</string><float name="Density">0.3</float><Color3 name="Color"><R>0.6</R><G>0.3</G><B>0.9</B></Color3></Properties></Item>
</Item>'''

sss = f'''<Item class="ServerScriptService" referent="{ref()}"><Properties><string name="Name">ServerScriptService</string></Properties>
{script("Script","Game",read('Game.server.lua'))}
{script("Script","Admin",read('Admin.server.lua'))}
{script("Script","Obstacles",read('Obstacles.server.lua'))}
{script("Script","Chaos",read('Chaos.server.lua'))}
</Item>'''
sp = f'''<Item class="StarterPlayer" referent="{ref()}"><Properties><string name="Name">StarterPlayer</string></Properties>
<Item class="StarterPlayerScripts" referent="{ref()}"><Properties><string name="Name">StarterPlayerScripts</string></Properties>
{script("LocalScript","GunClient",read('GunClient.client.lua'))}
</Item></Item>'''

xml = f'''<roblox xmlns:xmime="http://www.w3.org/2005/05/xmlmime" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="http://www.roblox.com/roblox.xsd" version="4">
<External>null</External><External>nil</External>
{ws}
{lighting}
{sss}
{sp}
</roblox>'''
open(os.path.join(HERE,'ParkourGame.rbxlx'),'w').write(xml)
import xml.dom.minidom as m; m.parseString(xml); print("ok", len(xml))
