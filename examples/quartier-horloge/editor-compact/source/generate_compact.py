#!/usr/bin/env python3
"""Standalone, deliberately small Quartier variant for the 180000-character editor library.
Native Copycat sources, small repeated modules, PATTERN compositions; no world edits.
"""
import json
from pathlib import Path
OUT=Path(__file__).resolve().parents[1]
forms,modules,nodes={},{},[]
def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
def p(x=0,y=0,z=0):return dict(x=x,y=y,z=z)
def part(source='copycats:copycat_block',group='body',**kw):return dict(source=source,group=group,**kw)
def form(id,parts,roles=None):
    ns=[dict(id=f's{i}',kind='INPUT',part=a,x=20,y=20+160*i) for i,a in enumerate(parts)]
    if len(parts)>1:ns.append(dict(id='union',kind='UNION',inputs=[f's{i}' for i in range(len(parts))],x=280,y=100))
    ns.append(dict(id='result',kind='OUTPUT',inputs=['union' if len(parts)>1 else 's0'],x=520,y=100))
    forms[id+'@1']=dict(schema=3,id=id,version=1,name='Horloge kompakt/'+id,groups=[dict(id=k,name=k,role=v,priority=i) for i,(k,v) in enumerate((roles or {'body':'surface'}).items())],graph=dict(schema=3,nodes=ns))
    return id+'@1'
full=form('hc_full',[part()])
wall=form('hc_wall',[part('create:copycat_panel',tilt=3)])
trim=form('hc_trim',[part('create:copycat_panel',tilt=3),part('copycats:copycat_slice',size=1,top=True,offset=p(0,0,-3),group='trim')],{'body':'surface','trim':'trim'})
window=form('hc_window',[part('copycats:copycat_layer',tilt=3,group='glass'),part('copycats:copycat_slice',group='frame'),part('copycats:copycat_vertical_slice',group='frame')],{'glass':'glass','frame':'frame'})
rail=form('hc_rail',[part('copycats:copycat_slice',top=True,offset=p(0,0,-6)),part('copycats:copycat_vertical_slice',offset=p(-7,0,-6))])
clock=form('hc_clock',[part('create:copycat_panel',tilt=3),part('copycats:copycat_flat_pane',tilt=3,offset=p(0,0,2),group='dial')],{'body':'surface','dial':'dial'})
def node(id,kind,**kw):
    nodes.append(dict(id=id,kind=kind,name=id.replace('_',' '),x=30+(len(nodes)//10)*280,y=30+(len(nodes)%10)*170,**kw));return id
node('empty_wall','SEGMENT',block='minecraft:air',length=1)
for id,f,role in [('floor',full,'floor'),('air',full,'brick'),('rail',rail,'metal'),('brick',wall,'brick')]:
    node('copy_'+id,'COPYCAT',form=f,role=role,**({'block':'minecraft:air'} if id=='air' else {}))
node('plate','FLOOR',inputs=['copy_floor'])
node('ceiling','CEILING',inputs=['copy_floor'])
def module(id,w,rows,role='brick'):
    table={'b':(wall,role),'t':(trim,role),'g':(window,'glass'),'p':(wall,'wood'),'a':(full,role),'c':(clock,'brick')}
    cells=[dict(pos=p(x,y),form=table[c][0],role=table[c][1],empty=c=='a') for y,row in enumerate(rows) for x,c in enumerate(row)]
    key=id+'@1'; modules[key]=dict(schema=1,id=id,version=1,name='Horloge kompakt/'+id,width=w,height=5,depth=1,minWidth=w,maxWidth=w,cells=cells)
    return node(id,'SEGMENT',module=key,length=w,height=5)
bay=module('bay',5,['ttttt','bgggb','bgggb','bgggb','ttttt'])
door=module('door_bay',5,['baaab','baaab','baaab','bbbbb','ttttt'])
post=module('post',1,['t','b','b','b','t'])
hbay=module('house_bay',4,['tttt','pggb','pggb','pggb','tttt'],'plaster')
hdoor=module('house_door',1,['a','a','a','b','t'],'plaster')
cbay=module('clock_bay',5,['ttttt','bbbbb','bb cbb'.replace(' ',''),'bbbbb','ttttt'])
def pattern(id,inputs):return node(id,'PATTERN',assembly=True,inputs=inputs)
short=pattern('hall_short',[bay]*3)
long=pattern('hall_long',[post]*3+[bay]*4)
entry=pattern('hall_entry',[bay,door,bay])
short4=pattern('gallery_short_4',[post]*4)
short5=bay
hshort=pattern('house_short',[hbay,post,hbay])
hlong=pattern('house_long',[post,hbay,post,hbay,post])
hentry=pattern('house_entry',[hbay,hdoor,hbay])
twall=pattern('tower_wall',[post,bay,post])
tentry=pattern('tower_entry',[post,door,post])
tclock=pattern('tower_clock',[post,cbay,post])
def sparse_segment(id,w,h,cells,source='copy_floor'):
    layout=node(id+'_grid','SEGMENT_LAYOUT',length=w,height=h,layoutCells=[dict(x=x,y=y,sourceIndex=0,turn=0) for x,y in cells])
    return node(id,'SEGMENT',assembly=True,module='',layout=layout,inputs=[source])
gallery=sparse_segment('gallery_plate',17,25,[(x,z) for x in range(17) for z in range(25) if not (6<=x<=10 and 1<=z<=17)])
voidplate=sparse_segment('nave_floor',7,19,[(x,z) for x in range(7) for z in range(19) if not (1<=x<=5 and 1<=z<=17)])
def railing(id,w,active=None):return sparse_segment(id,w,5,[(x,0) for x in range(w) if active is None or x in active],'copy_rail')
r23=railing('rail_23',23,set(range(18)))
r23rev=railing('rail_23_reverse',23,set(range(5,23)))
r17=railing('rail_17',17)
r5=railing('rail_5',5)
def geometry(id,w,d):return node(id,'ROOM_GEOMETRY',height=5,coordinates=[p(x,0,z) for z in range(d) for x in range(w)])
def edges(w,d):return ['0:0:0',f'1:{w-1}:0',f'2:{w-1}:{d-1}',f'3:0:{d-1}']
def room(id,w,d,walls,floor='plate',ceiling='ceiling',explicit=False):
    opts=dict(rectangles=[dict(x=0,z=0,width=w,length=d)],height=5,walls=dict(zip(edges(w,d),walls)),floor=floor,ceiling=ceiling)
    if explicit:opts.update(assembly=True,geometry=geometry(id+'_geometry',w,d))
    return node(id,'ROOM',**opts)
hall=room('restaurant',15,23,[entry,long,short,long],ceiling=gallery,explicit=True)
gleft=room('west_gallery',4,23,[short4,r23,short4,long])
gright=room('east_gallery',4,23,[short4,long,short4,r23rev])
gback=room('rear_gallery',5,5,[r5,'empty_wall',short5,'empty_wall'])
gnave=room('double_height_nave',5,17,[short5,r17,r5,r17],floor=voidplate,explicit=True)
t0=room('tower_ground',7,7,[tentry,twall,twall,twall])
t1=room('tower_rooms',7,7,[twall]*4)
t4=room('tower_clockroom',7,7,[tclock]*4)
h0=room('dwelling_ground',9,11,[hentry,hlong,hshort,hlong])
h1=room('dwelling_upper',9,11,[hshort,hlong,hshort,hlong])
s0=node('ground','STOREY',inputs=[hall,t0,h0,h0],offsets=[p(),p(18,0,0),p(0,0,27),p(18,0,27)])
s1=node('upper','STOREY',inputs=[gleft,gright,gback,gnave,t1,h1,h1],offsets=[p(),p(11,0,0),p(5,0,18),p(5,0,0),p(18,0,0),p(0,0,27),p(18,0,27)])
s2=node('tower_upper','STOREY',inputs=[t1],offsets=[p(18,0,0)])
s4=node('tower_top','STOREY',inputs=[t4],offsets=[p(18,0,0)])
stairs=node('stairs','STAIRWELL',stairwell=dict(layout='U',width=2,landingDepth=2,headroom=2,clockwise=True,enclosed=False))
rooflevels=[]
for id,x,z,w,d,storey,shape,slope in [('hall',-1,-1,17,25,'storey_1','GABLE',(1,1)),('west_house',-1,26,11,13,'storey_1','GABLE',(1,1)),('east_house',17,26,11,13,'storey_1','GABLE',(1,1)),('tower',17,-1,9,9,'storey_4','HIP',(2,1))]:
    area=node(id+'_roof_area','AREA',coordinates=[p(a,0,b) for b in range(z,z+d) for a in range(x,x+w)])
    rooflevels.append(node(id+'_roof_level','ROOF_LEVEL',roofLevel=dict(storey=storey,area=area,form=shape,axis=0,mode='ANGLE',slope=dict(p=slope[0],q=slope[1]),overhang16=8)))
rl=node('roof_layout','ROOF_LAYOUT',inputs=rooflevels)
roof=node('roof','ROOF',assembly=True,layout=rl,role='roof',roofConfig=dict(family='builtin',wall='copy_brick'))
# The two courtyard nodes remain genuine editable landscape sources.
court=node('court','GARDEN',block='minecraft:stone_bricks',coordinates=[p(x,0,z) for x in range(11,16) for z in range(25,40)],clearance=0)
paths=node('forecourt','GARDEN',block='minecraft:stone',coordinates=[p(x,0,z) for x in range(-2,29) for z in range(-5,-2)],clearance=0)
access=[dict(id=id,source=stairs,position=p(x,0,z),fromStorey='storey_0',toStorey=to,quarterTurns=0) for id,x,z,to in [('hall_stairs',0,17,'storey_1'),('tower_stairs',19,2,'storey_4'),('west_stairs',1,30,'storey_1'),('east_stairs',19,30,'storey_1')]]
node('building','BUILDING',levels={0:s0,1:s1,2:s2,3:s2,4:s4},roof=roof,accessInstances=access,inputs=[court,paths])
palette=dict(wall='minecraft:bricks',brick='minecraft:bricks',trim='minecraft:smooth_sandstone',glass='minecraft:glass',frame='minecraft:dark_oak_planks',floor='minecraft:spruce_planks',roof='minecraft:deepslate_tiles',wood='minecraft:dark_oak_planks',plaster='minecraft:white_terracotta',metal='minecraft:gold_block',dial='minecraft:quartz_block',stairs='minecraft:spruce_planks',landing='minecraft:spruce_planks',access_wall='minecraft:bricks')
building=dict(schema=2,id='quartier_horloge_compact',version=1,name='Quartier de l Horloge - Editor kompakt',floors=5,roomHeight=5,wings=[dict(x=0,z=0,width=15,length=23)],palette=palette,graph=dict(schema=2,nodes=nodes))
assert len(nodes)<=96,len(nodes)
for f in forms.values():write(OUT/'forms'/f"{f['id']}.json",f)
for m in modules.values():write(OUT/'modules'/f"{m['id']}.json",m)
write(OUT/'building.json',building)
write(OUT/'library.json',dict(forms=forms,modules=modules,buildings={'quartier_horloge_compact@1':building}))
write(OUT/'design-intent.json',dict(reference='https://createmod.com/de/schematics/quartier-de-lhorloge',variant='Separate editor compact variant; rectangular windows and one-block native DISC clocks',hallInterior=[15,23],galleryVoid=[5,17],towerInterior=[7,7],towerStoreys=5,houseInteriors=[[9,11],[9,11]],houseStoreys=2,stairs=4,roofs=4,graphNodes=len(nodes),forms=len(forms),modules=len(modules)))
print(f'{len(nodes)} nodes, {len(forms)} native-source forms, {len(modules)} small reusable modules')
