import itertools, math, os
HERE = os.path.dirname(os.path.abspath(__file__))
def read(p): return open(os.path.join(HERE, 'src', p), encoding='utf-8').read()

n = itertools.count()
def ref(): return f"RBX{next(n)}"
def col(r,g,b): return (0xFF<<24)|(r<<16)|(g<<8)|b
def cf(x,y,z,a=0):
    c, s = math.cos(a), math.sin(a)
    return f'<CoordinateFrame name="CFrame"><X>{x}</X><Y>{y}</Y><Z>{z}</Z><R00>1</R00><R01>0</R01><R02>0</R02><R10>0</R10><R11>{c}</R11><R12>{-s}</R12><R20>0</R20><R21>{s}</R21><R22>{c}</R22></CoordinateFrame>'
def part(cls, name, pos, size, color, material=256, extra="", angle=0):
    return f'''<Item class="{cls}" referent="{ref()}"><Properties>
<string name="Name">{name}</string>
<bool name="Anchored">true</bool>
{cf(*pos, a=angle)}
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

NEON, CONCRETE = 288, 816
F = {k: [] for k in ["Map", "Pickups"]}
uid = itertools.count(1)
def add(fold, name, pos, size, color, mat=256, extra="", angle=0):
    F[fold].append(part("Part", name, pos, size, color, mat, extra, angle))

WALL=(50,50,75); ROOF=(35,35,58); FLOOR=(30,30,42); CRATE=(90,70,50)
NOCOLLIDE = '<bool name="CanCollide">false</bool>'
WCOL = {"SMG":(255,140,0), "Shotgun":(255,60,60), "Sniper":(0,240,255), "Rocket":(255,220,0)}
def pickup(w, pos, size=(2,2,2)):
    add("Pickups", f"Pickup:{w}:{next(uid)}", pos, size, WCOL[w], NEON, NOCOLLIDE)

# arena
add("Map","Floor",(0,-0.5,0),(270,1,270),FLOOR,CONCRETE)
for nm,pos,size in (("WallN",(0,20,-135),(272,40,2)),("WallS",(0,20,135),(272,40,2)),
                    ("WallE",(135,20,0),(2,40,272)),("WallW",(-135,20,0),(2,40,272))):
    add("Map",nm,pos,size,(25,25,40),CONCRETE)

W_, D_, H_ = 36, 36, 12
WEAPONS = ["SMG","Shotgun","Sniper","Rocket"]
bi = 0
for cx in (-80,0,80):
    for cz in (-80,0,80):
        # north/south walls with a doorway in the middle
        for sz in (-1,1):
            z = cz + sz*D_/2
            for sx in (-1,1):
                add("Map","Wall",(cx+sx*11,6,z),(14,12,1),WALL,CONCRETE)
            add("Map","Lintel",(cx,10.5,z),(8,3,1),WALL,CONCRETE)
        add("Map","Wall",(cx-W_/2,6,cz),(1,12,D_),WALL,CONCRETE)
        add("Map","Wall",(cx+W_/2,6,cz),(1,12,D_),WALL,CONCRETE)
        add("Map","Roof",(cx,12.5,cz),(W_+1,1,D_),ROOF,CONCRETE)
        # ramp up to the roof, outside the east wall
        run, rise = 22, 13
        th = math.atan2(rise, run)
        add("Map","Ramp",(cx+W_/2+4,5.9,cz-D_/2+run/2),(7,1,math.hypot(run,rise)),(0,240,255),NEON,angle=-th)
        # pickups: floor plate, wall niche, roof
        pickup(WEAPONS[bi%2], (cx,0.2,cz), (3,0.4,3))
        pickup(WEAPONS[(bi+1)%4], (cx-W_/2+1,4,cz))
        pickup("Sniper" if bi%2==0 else "Rocket", (cx,14.2,cz))
        bi += 1

# street plates
for i,(x,z) in enumerate([(-40,-80),(40,-80),(-40,80),(40,80),(0,-40),(0,40),(-80,40),(80,-40)]):
    pickup(WEAPONS[i%4], (x,0.2,z), (3,0.4,3))
# crates for cover
for i,z in enumerate(range(-120,121,40)):
    add("Map","Crate",(-40+(8 if i%2 else -8),2.5,z),(6,5,6),CRATE)
    add("Map","Crate",(40+(-8 if i%2 else 8),2.5,z),(6,5,6),CRATE)
for i,x in enumerate((-120,-80,0,80,120)):
    add("Map","Crate",(x+8,2.5,-40+(8 if i%2 else -8)),(6,5,6),CRATE)
    add("Map","Crate",(x-8,2.5,40+(-8 if i%2 else 8)),(6,5,6),CRATE)

# spawns
spawns = []
for x,z in [(-40,-40),(40,-40),(-40,40),(40,40),(-120,-120),(120,-120),(-120,120),(120,120),(0,-120),(0,120),(-120,0),(120,0)]:
    spawns.append(part("SpawnLocation","Spawn",(x,0.5,z),(6,1,6),(120,120,255),NEON,
                       '<bool name="Neutral">true</bool><int name="Duration">0</int>'))

ws = f'''<Item class="Workspace" referent="{ref()}"><Properties><string name="Name">Workspace</string></Properties>
{"".join(spawns)}
{"".join(folder(k, v) for k, v in F.items())}
</Item>'''

lighting = f'''<Item class="Lighting" referent="{ref()}"><Properties><string name="Name">Lighting</string>
<string name="TimeOfDay">00:00:00</string>
<float name="Brightness">2</float>
<Color3 name="Ambient"><R>0.35</R><G>0.2</G><B>0.5</B></Color3>
<Color3 name="OutdoorAmbient"><R>0.4</R><G>0.25</G><B>0.6</B></Color3>
</Properties>
<Item class="BloomEffect" referent="{ref()}"><Properties><string name="Name">Bloom</string><float name="Intensity">1.2</float><int name="Size">32</int><float name="Threshold">0.8</float></Properties></Item>
<Item class="Atmosphere" referent="{ref()}"><Properties><string name="Name">Atmosphere</string><float name="Density">0.25</float><Color3 name="Color"><R>0.6</R><G>0.3</G><B>0.9</B></Color3></Properties></Item>
</Item>'''

sss = f'''<Item class="ServerScriptService" referent="{ref()}"><Properties><string name="Name">ServerScriptService</string></Properties>
{script("Script","Combat",read('Combat.server.lua'))}
{script("Script","Admin",read('Admin.server.lua'))}
{script("Script","Chaos",read('Chaos.server.lua'))}
</Item>'''
sp = f'''<Item class="StarterPlayer" referent="{ref()}"><Properties><string name="Name">StarterPlayer</string></Properties>
<Item class="StarterPlayerScripts" referent="{ref()}"><Properties><string name="Name">StarterPlayerScripts</string></Properties>
{script("LocalScript","GunClient",read('GunClient.client.lua'))}
{script("LocalScript","WeaponClient",read('WeaponClient.client.lua'))}
</Item></Item>'''

xml = f'''<roblox xmlns:xmime="http://www.w3.org/2005/05/xmlmime" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="http://www.roblox.com/roblox.xsd" version="4">
<External>null</External><External>nil</External>
{ws}
{lighting}
{sss}
{sp}
</roblox>'''
open(os.path.join(HERE,'ParkourGame.rbxlx'),'w').write(xml)
import xml.dom.minidom as m; m.parseString(xml); print("ok", len(xml), "parts:", xml.count('class="Part"'), "pickups:", len(F["Pickups"]))
