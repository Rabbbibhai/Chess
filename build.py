import itertools, math, os
HERE = os.path.dirname(os.path.abspath(__file__))
def read(p): return open(os.path.join(HERE, 'src', p), encoding='utf-8').read()

n = itertools.count()
def ref(): return f"RBX{next(n)}"
def col(r,g,b): return (0xFF<<24)|(r<<16)|(g<<8)|b
def cfy(x,y,z,a=0):
    c, s = math.cos(a), math.sin(a)
    return f'<CoordinateFrame name="CFrame"><X>{x}</X><Y>{y}</Y><Z>{z}</Z><R00>{c}</R00><R01>0</R01><R02>{s}</R02><R10>0</R10><R11>1</R11><R12>0</R12><R20>{-s}</R20><R21>0</R21><R22>{c}</R22></CoordinateFrame>'
def part(cls, name, pos, size, color, material=256, extra="", ry=0):
    return f'''<Item class="{cls}" referent="{ref()}"><Properties>
<string name="Name">{name}</string>
<bool name="Anchored">true</bool>
{cfy(*pos, a=ry)}
<Vector3 name="size"><X>{size[0]}</X><Y>{size[1]}</Y><Z>{size[2]}</Z></Vector3>
<Color3uint8 name="Color3uint8">{col(*color)}</Color3uint8>
<token name="Material">{material}</token>
{extra}</Properties></Item>'''
def script(cls, name, code, children=""):
    return f'''<Item class="{cls}" referent="{ref()}"><Properties>
<string name="Name">{name}</string>
<ProtectedString name="Source"><![CDATA[{code}]]></ProtectedString>
</Properties>{children}</Item>'''
def container(cls, name, children):
    return f'<Item class="{cls}" referent="{ref()}"><Properties><string name="Name">{name}</string></Properties>{"".join(children)}</Item>'

NEON, WOOD, MARBLE, GLASS, GRASS, SMOOTH = 288, 512, 784, 1568, 1280, 272
BALL = '<token name="shape">0</token>'
NOCOL = '<bool name="CanCollide">false</bool>'
ACCENT = (122,102,255); DARK = (24,26,40)

M = []   # lobby decor
def add(name,pos,size,color,mat=256,extra="",ry=0): M.append(part("Part",name,pos,size,color,mat,extra,ry))

# ground, floor, walls
add("Grass",(0,-1.5,0),(600,2,600),(110,190,110),GRASS)
add("Floor",(0,-0.5,0),(220,1,180),(238,238,248),MARBLE)
add("WallN",(0,2.5,-90.5),(222,5,1),(170,210,255),GLASS,'<float name="Transparency">0.55</float>')
add("WallS",(0,2.5,90.5),(222,5,1),(170,210,255),GLASS,'<float name="Transparency">0.55</float>')
add("WallE",(110.5,2.5,0),(1,5,182),(170,210,255),GLASS,'<float name="Transparency">0.55</float>')
add("WallW",(-110.5,2.5,0),(1,5,182),(170,210,255),GLASS,'<float name="Transparency">0.55</float>')
# neon floor trim
for z in (-88,88): add("Trim",(0,0.05,z),(216,0.1,1),ACCENT,NEON,NOCOL)
for x in (-108,108): add("Trim",(x,0.05,0),(1,0.1,176),ACCENT,NEON,NOCOL)
add("Runway",(0,0.04,60),(10,0.08,50),(255,200,80),NEON,NOCOL)
# title sign
add("TitleSign",(0,11,-88),(64,12,1),DARK,SMOOTH)
for nm,pos,size in (("SignTop",(0,17.2,-88),(66,0.8,1.4)),("SignBot",(0,4.8,-88),(66,0.8,1.4)),
                    ("SignL",(-32.6,11,-88),(0.8,12.8,1.4)),("SignR",(32.6,11,-88),(0.8,12.8,1.4))):
    add(nm,pos,size,ACCENT,NEON)
add("SignPostL",(-22,2.5,-88),(1.2,5,1.2),(60,60,80),SMOOTH)
add("SignPostR",(22,2.5,-88),(1.2,5,1.2),(60,60,80),SMOOTH)
# lamps, planters
for z in (-60,-20,20,60):
    for x in (-102,102):
        add("LampPole",(x,6,z),(0.6,12,0.6),(70,70,90),SMOOTH)
        add("LampBulb",(x,12.6,z),(2.4,2.4,2.4),(255,230,150),NEON,BALL)
for x in (-100,100):
    for z in (-80,80):
        add("Planter",(x,1,z),(6,2,6),(130,90,60),WOOD)
        add("Bush",(x,4.5,z),(6.5,6.5,6.5),(70,170,90),GRASS,BALL)
for x in (-14,14):
    add("Planter",(x,1,78),(4,2,4),(130,90,60),WOOD)
    add("Bush",(x,3.8,78),(4.8,4.8,4.8),(70,170,90),GRASS,BALL)

# tables
TABLE_COL=(236,204,160); LEG=(120,86,60); CHAIR=ACCENT
tables = []
idx = 0
for tz in (-28, 22):
    for tx in (-55, 0, 55):
        idx += 1
        k = []
        k.append(part("Part","Top",(tx,3.6,tz),(8,0.6,8),TABLE_COL,WOOD))
        for lx in (-3.2,3.2):
            for lz in (-3.2,3.2):
                k.append(part("Part","Leg",(tx+lx,1.65,tz+lz),(0.8,3.3,0.8),LEG,WOOD))
        for sx,sname,ry in ((1,"Seat1",math.pi/2),(-1,"Seat2",-math.pi/2)):
            k.append(part("Seat",sname,(tx+sx*6.2,1.5,tz),(2.4,1,2.4),CHAIR,SMOOTH,"",ry))
            k.append(part("Part","ChairBack",(tx+sx*7.6,3.25,tz),(0.4,2.5,2.4),CHAIR,SMOOTH))
            k.append(part("Part","ChairLeg",(tx+sx*6.2,0.5,tz),(0.6,1,0.6),LEG,SMOOTH))
        k.append(part("Part","Status",(tx,9,tz),(1,1,1),(255,255,255),256,'<float name="Transparency">1</float>'+NOCOL))
        k.append(part("Part","Light",(tx,0.1,tz),(17,0.2,17),(80,230,120),NEON,NOCOL))
        tables.append(container("Model", f"Table {idx}", k))

spawn = part("SpawnLocation","Spawn",(0,0.5,72),(12,1,12),ACCENT,NEON,
             '<bool name="Neutral">true</bool><int name="Duration">0</int>')

ws = f'''<Item class="Workspace" referent="{ref()}"><Properties><string name="Name">Workspace</string></Properties>
{spawn}
{container("Folder","Lobby",M)}
{container("Folder","Tables",tables)}
</Item>'''

lighting = f'''<Item class="Lighting" referent="{ref()}"><Properties><string name="Name">Lighting</string>
<string name="TimeOfDay">14:00:00</string>
<float name="Brightness">3</float>
<Color3 name="Ambient"><R>0.5</R><G>0.5</G><B>0.6</B></Color3>
<Color3 name="OutdoorAmbient"><R>0.6</R><G>0.6</G><B>0.7</B></Color3>
</Properties>
<Item class="BloomEffect" referent="{ref()}"><Properties><string name="Name">Bloom</string><float name="Intensity">0.5</float><int name="Size">24</int><float name="Threshold">1</float></Properties></Item>
<Item class="Atmosphere" referent="{ref()}"><Properties><string name="Name">Atmosphere</string><float name="Density">0.2</float></Properties></Item>
</Item>'''

modules = container("Folder","Modules",[
    script("ModuleScript","WordData",read('Modules/WordData.lua')),
    script("ModuleScript","Similarity",read('Modules/Similarity.lua')),
    script("ModuleScript","TableController",read('Modules/TableController.lua')),
])
sss = f'''<Item class="ServerScriptService" referent="{ref()}"><Properties><string name="Name">ServerScriptService</string></Properties>
{script("Script","Main",read('Main.server.lua'))}
{modules}
</Item>'''
ui = script("LocalScript","GameUI",read('Client/GameUI.client.lua'),
            script("ModuleScript","UIKit",read('Client/UIKit.lua')))
sp = f'''<Item class="StarterPlayer" referent="{ref()}"><Properties><string name="Name">StarterPlayer</string></Properties>
<Item class="StarterPlayerScripts" referent="{ref()}"><Properties><string name="Name">StarterPlayerScripts</string></Properties>
{ui}
</Item></Item>'''

xml = f'''<roblox xmlns:xmime="http://www.w3.org/2005/05/xmlmime" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="http://www.roblox.com/roblox.xsd" version="4">
<External>null</External><External>nil</External>
{ws}
{lighting}
{sss}
{sp}
</roblox>'''
with open(os.path.join(HERE,'WordWars.rbxlx'),'w',encoding='utf-8') as f: f.write(xml)
import xml.dom.minidom as m; m.parseString(xml); print("ok", len(xml), "tables:", len(tables))
