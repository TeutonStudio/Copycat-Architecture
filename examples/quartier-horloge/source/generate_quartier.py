#!/usr/bin/env python3
"""Deterministic source for the Quartier de l'Horloge architectural graph.

No Minecraft client, world edits or third-party geometry libraries are needed.
Run this followed by source/compile_and_export.sh to validate with the real mod.
"""
import json, math
from pathlib import Path

OUT = Path(__file__).resolve().parents[1]
forms, modules, nodes = {}, {}, []
def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n')
def p(x=0,y=0,z=0): return dict(x=x,y=y,z=z)
def part(source='copycats:copycat_block', group='body', **kw):
    return dict(source=source,group=group,**kw)
def group(id, role='surface', priority=0):
    return dict(id=id,name=id,role=role,priority=priority)
def graph_form(id,name,parts,groups=None):
    ns=[dict(id=f's{i}',kind='INPUT',part=a,x=20,y=20+160*i) for i,a in enumerate(parts)]
    if len(parts)>1:
        ns.append(dict(id='union',kind='UNION',inputs=[f's{i}' for i in range(len(parts))],x=280,y=100))
    ns.append(dict(id='result',kind='OUTPUT',inputs=['union' if len(parts)>1 else 's0'],x=520,y=100))
    f=dict(schema=4 if any('exact' in a for a in parts) else 3,id=id,version=1,name='Horloge/'+name,
           groups=groups or [group('body')],graph=dict(schema=3,nodes=ns))
    forms[id+'@1']=f
    return id+'@1'
def plane(x,y,z,d): return dict(x=x,y=y,z=z,d=d)
def volume(planes): return dict(cells=[dict(planes=planes)])
def box(x0,y0,z0,x1,y1,z1):
    return volume([plane(-1,0,0,-x0),plane(1,0,0,x1),plane(0,-1,0,-y0),plane(0,1,0,y1),plane(0,0,-1,-z0),plane(0,0,1,z1)])
def arch_volume(cx,cy, radius, ix,iy,z0,z1, bottom=0, full_circle=False):
    # Integer normals yield exact rational planar facets, never block steps.
    ox,oy=16*ix,16*iy
    ps=[plane(-1,0,0,0),plane(1,0,0,16),plane(0,-1,0,0),plane(0,1,0,16),plane(0,0,-1,-z0),plane(0,0,1,z1)]
    ps += [plane(-1,0,0,ox-(cx-radius)),plane(1,0,0,cx+radius-ox),plane(0,-1,0,oy-bottom)]
    for j in range(25 if full_circle else 13):
        a=(2*math.pi if full_circle else math.pi)*j/24 if full_circle else math.pi*j/12
        nx,ny=round(1000*math.cos(a)),round(1000*math.sin(a))
        ps.append(plane(nx,ny,0,round(radius*math.hypot(nx,ny)+nx*(cx-ox)+ny*(cy-oy))))
    return volume(ps)

full=graph_form('h_full','Bauteile/Vollblock',[part()])
copperfull=graph_form('h_copper_full','Dach/Kupfer Periodenzelle',[part()],[group('body','copper')])
panel=graph_form('h_panel','Fassade/3-16 Ziegelwand',[part('create:copycat_panel',tilt=3)])
base=graph_form('h_base','Fassade/Werksteinsockel',[part('create:copycat_panel',tilt=3),part('copycats:copycat_slice',size=2,offset=p(0,0,-2),group='trim')],[group('body'),group('trim','trim',1)])
cornice=graph_form('h_cornice','Fassade/Vorkragendes Gesims',[part('create:copycat_panel',tilt=3),part('copycats:copycat_slice',size=2,top=True,offset=p(0,0,-3),group='trim')],[group('body'),group('trim','trim',1)])
pier=graph_form('h_pier','Fassade/Schmaler Werksteinpfeiler',[part('create:copycat_panel',tilt=3),part('copycats:copycat_vertical_slice',size=2,offset=p(-6,0,-3),group='trim')],[group('body'),group('trim','trim',1)])
window=graph_form('h_window','Fenster/2-16 Glas und Rahmen',[part('copycats:copycat_layer',tilt=3,group='glass'),part('copycats:copycat_slice',group='frame'),part('copycats:copycat_slice',top=True,group='frame'),part('copycats:copycat_vertical_slice',group='frame'),part('copycats:copycat_vertical_slice',turn=1,group='frame')],[group('glass','glass'),group('frame','frame',1)])

# Three-by-three arched windows. All cells are reusable, editable Boolean graphs.
archforms={}
for iy in range(3):
    for ix in range(3):
        id=f'h_arch_{ix}_{iy}'
        outer=arch_volume(24,24,24,ix,iy,10,16)
        inner=arch_volume(24,24,21,ix,iy,10,16,bottom=2)
        pane=arch_volume(24,24,21,ix,iy,14,16,bottom=2)
        gs=[group('body'),group('arch','trim',1),group('glass','glass',2),group('frame','frame',3)]
        ns=[dict(id='wall',kind='INPUT',part=part('create:copycat_panel',tilt=3),x=20,y=20),
            dict(id='opening',kind='INPUT',part=part(exact=outer),x=20,y=180),
            dict(id='cut',kind='DIFFERENCE',inputs=['wall','opening'],x=240,y=60),
            dict(id='outer',kind='INPUT',part=part(group='arch',exact=outer),x=20,y=360),
            dict(id='inner',kind='INPUT',part=part(group='arch',exact=inner),x=20,y=520),
            dict(id='ring',kind='DIFFERENCE',inputs=['outer','inner'],x=240,y=360),
            dict(id='glass',kind='INPUT',part=part(group='glass',exact=pane),x=240,y=620)]
        union=['cut','ring','glass']
        # Continuous fine central mullion and transom in the recessed glass.
        for n,b in [('mullion',box(7,0,13,9,16,16))] if ix==1 else []:
            ns.append(dict(id=n,kind='INPUT',part=part(group='frame',exact=b),x=240,y=800));union.append(n)
        if iy==1:
            ns.append(dict(id='transom',kind='INPUT',part=part(group='frame',exact=box(0,7,13,16,9,16)),x=240,y=960));union.append('transom')
        ns += [dict(id='union',kind='UNION',inputs=union,x=470,y=200),dict(id='result',kind='OUTPUT',inputs=['union'],x=700,y=200)]
        forms[id+'@1']=dict(schema=4,id=id,version=1,name=f'Horloge/Fenster/Bogenfeld {ix+1}-{iy+1}',groups=gs,graph=dict(schema=3,nodes=ns))
        archforms[ix,iy]=id+'@1'

# Clock face: a 3-block-diameter, 24-sided exact circle, relief rim and hands.
clockforms={}
for iy in range(3):
    for ix in range(3):
        id=f'h_clock_{ix}_{iy}'
        outer=arch_volume(24,24,24,ix,iy,10,12,bottom=-100,full_circle=True)
        inner=arch_volume(24,24,21,ix,iy,9,13,bottom=-100,full_circle=True)
        disk=arch_volume(24,24,21,ix,iy,11,12,bottom=-100,full_circle=True)
        ns=[dict(id='wall',kind='INPUT',part=part('create:copycat_panel',tilt=3),x=20,y=20),dict(id='outer',kind='INPUT',part=part(group='rim',exact=outer),x=20,y=200),dict(id='inner',kind='INPUT',part=part(group='rim',exact=inner),x=20,y=400),dict(id='ring',kind='DIFFERENCE',inputs=['outer','inner'],x=260,y=240),dict(id='disk',kind='INPUT',part=part(group='dial',exact=disk),x=260,y=500)]
        union=['wall','ring','disk']
        # 10:08: two slender hands built as local intersections of exact planes.
        for k,(dx,dy,length) in enumerate([(-13,10,17),(12,19,23)]):
            ox,oy=16*ix,16*iy
            # A short oriented convex strip from the dial center toward its tip.
            nx,ny=-dy,dx; norm=math.hypot(nx,ny)
            ps=box(0,0,9,16,16,11)['cells'][0]['planes']
            center=nx*(24-ox)+ny*(24-oy)
            dot=dx*(24-ox)+dy*(24-oy)
            ps += [plane(nx,ny,0,round(center+norm)),plane(-nx,-ny,0,round(-center+norm)),plane(-dx,-dy,0,round(-dot+norm)),plane(dx,dy,0,round(dot+length*norm))]
            # Some cells have no hand intersection; include only intersected face cells.
            if (ix,iy) in ([(1,1),(0,1),(0,2)] if k==0 else [(1,1),(1,2),(2,2)]):
                ns.append(dict(id=f'hand{k}',kind='INPUT',part=part(group='hands',exact=volume(ps)),x=260,y=700+k*160));union.append(f'hand{k}')
        ns += [dict(id='union',kind='UNION',inputs=union,x=500,y=200),dict(id='result',kind='OUTPUT',inputs=['union'],x=730,y=200)]
        forms[id+'@1']=dict(schema=4,id=id,version=1,name=f'Horloge/Uhr/Zifferblatt {ix+1}-{iy+1}',groups=[group('body'),group('rim','metal',1),group('dial','dial',2),group('hands','frame',3)],graph=dict(schema=3,nodes=ns))
        clockforms[ix,iy]=id+'@1'

def node(id,kind,name=None,**kw):
    n=dict(id=id,kind=kind,name=name or id,x=30+(len(nodes)//10)*280,y=30+(len(nodes)%10)*170,**kw)
    nodes.append(n);return id
node('empty_wall','SEGMENT','Leere Wand',block='minecraft:air',length=1)
copykeys={}
for name,form,role in [('brick',panel,'brick'),('base',base,'brick'),('cornice',cornice,'brick'),('pier',pier,'brick'),('glass',window,'glass'),('floor',full,'floor'),('plaster',panel,'plaster'),('timber',pier,'wood'),('stone',panel,'trim'),('air',full,'brick')]:
    copykeys[name]=node('copy_'+name,'COPYCAT',name,form=form,role=role,**({'block':'minecraft:air'} if name=='air' else {}))
for cell,form in archforms.items(): copykeys['arch%d%d'%cell]=node('copy_arch%d%d'%cell,'COPYCAT','Bogen '+str(cell),form=form,role='brick')
rail=graph_form('h_rail','Galerie/Messinghandlauf und Staebe',[part(exact=box(0,13,7,16,16,9)),part(exact=box(7,0,7,9,13,9))])
node('copy_rail','COPYCAT','Galeriegelaender',form=rail,role='metal')
node('plate','FLOOR','Holzboden und Decke',inputs=['copy_floor'])
node('ceiling','CEILING','Holzdecke',inputs=['copy_floor'])

def explicit_segment(id,w,h,choices):
    sources=list(dict.fromkeys(choices.values()))
    layout=node(id+'_grid','SEGMENT_LAYOUT',id+' Raster',length=w,height=h,layoutCells=[dict(x=x,y=y,sourceIndex=sources.index(s),turn=0) for (x,y),s in choices.items()])
    return node(id,'SEGMENT',id,assembly=True,module='',layout=layout,inputs=sources)

facade_sources=['copy_brick','copy_base','copy_cornice','copy_pier','copy_air']+['copy_arch%d%d'%(ix,iy) for iy in range(3) for ix in range(3)]
def hallwall(id,w,door=False):
    choices={}
    centers=list(range(2,w-1,5)) if w==15 else [3,8,13,18]
    for y in range(5):
        for x in range(w):
            s='copy_base' if y==0 else 'copy_cornice' if y==4 else 'copy_brick'
            for cx in centers:
                if cx-1<=x<=cx+1 and 1<=y<=3: s='copy_arch%d%d'%(x-(cx-1),y-1)
            if x in [c-2 for c in centers]+[c+2 for c in centers] and y in [1,2,3]: s='copy_pier'
            if door and abs(x-w//2)<=1 and y<3: s='copy_air'
            choices[x,y]=s
    return explicit_segment(id,w,5,choices)
hall_short=hallwall('hall_short',15)
hall_long=hallwall('hall_long',23)
hall_entry=hallwall('hall_entry',15,True)

# A common gallery plate is used by both adjacent storeys. Its central hole
# really removes the ceiling and upper floor, leaving a 5 x 17 double-height nave.
gallery_choices={(x,z):('copy_air' if 5<=x-1<=9 and 0<=z-1<=16 else 'copy_floor') for x in range(17) for z in range(25)}
gallery=explicit_segment('gallery_plate',17,25,gallery_choices)

def rectgeom(id,w,d):
    return node(id,'ROOM_GEOMETRY',id,height=5,coordinates=[p(x,0,z) for z in range(d) for x in range(w)])
def edges(w,d): return [f'0:0:0',f'1:{w-1}:0',f'2:{w-1}:{d-1}',f'3:0:{d-1}']
def room(id,geometry,w,d,walls,floor='plate',ceiling='ceiling'):
    return node(id,'ROOM',id,assembly=True,geometry=geometry,height=5,walls=dict(zip(edges(w,d),walls)),floor=floor,ceiling=ceiling)
hallgeo=rectgeom('hall_geometry',15,23)
hall0=room('restaurant',hallgeo,15,23,[hall_entry,hall_long,hall_short,hall_long],ceiling=gallery)
# The upper level is split into side galleries, rear gallery and the nave.
# Empty nave floor cells preserve the open double-height restaurant. Rails are
# real boundary walls, so they do not intersect the engine's interior clear cells.
def railing(id,w,active=None):
    return explicit_segment(id,w,5,{(x,y):('copy_rail' if y==0 and (active is None or x in active) else 'copy_air') for x in range(w) for y in range(5)})
rail23=railing('rail_23',23,set(range(18)))
rail23rev=railing('rail_23_reverse',23,set(range(5,23)))
rail17=railing('rail_17',17)
rail5=railing('rail_5',5)
short4=hallwall('gallery_short_4',4)
short5=hallwall('gallery_short_5',5)
gside=rectgeom('gallery_side_geometry',4,23)
grear=rectgeom('gallery_rear_geometry',5,5)
gvoid=rectgeom('nave_geometry',5,17)
voidplate=explicit_segment('nave_floor',7,19,{(x,z):('copy_air' if 1<=x<=5 and 1<=z<=17 else 'copy_floor') for x in range(7) for z in range(19)})
gleft=room('west_gallery',gside,4,23,[short4,rail23,short4,hall_long])
gright=room('east_gallery',gside,4,23,[short4,hall_long,short4,rail23rev])
gback=room('rear_gallery',grear,5,5,[rail5,'empty_wall',short5,'empty_wall'])
gnave=room('double_height_nave',gvoid,5,17,[short5,rail17,rail5,rail17],floor=voidplate)

def module_segment(id,w,h,cells):
    modules[id+'@1']=dict(schema=1,id=id,version=1,name='Horloge/'+id,width=w,height=h,depth=1,minWidth=w,maxWidth=w,cells=cells)
    return node(id,'SEGMENT',id,module=id+'@1',length=w,height=h)
def towerwall(id,door=False,clock=False):
    cells=[]
    for y in range(5):
        for x in range(7):
            f=base if y==0 else cornice if y==4 else pier if x in [0,6] else panel
            role='trim' if y in [0,4] else 'wood' if x in [0,6] else 'brick'
            empty=door and x==3 and y<3
            if clock and 2<=x<=4 and 1<=y<=3: f=clockforms[x-2,y-1];role='brick'
            elif not clock and x in [2,3,4] and y in [1,2,3]: f=window;role='glass'
            cells.append(dict(pos=p(x,y,0),form=f,role=role,empty=empty))
    return module_segment(id,7,5,cells)
twall=towerwall('tower_wall');tentry=towerwall('tower_entry',True);tclock=towerwall('tower_clock',clock=True)
tgeo=rectgeom('tower_geometry',7,7)
t0=room('tower_ground',tgeo,7,7,[tentry,twall,twall,twall])
t1=room('tower_rooms',tgeo,7,7,[twall]*4)
t4=room('tower_clockroom',tgeo,7,7,[tclock]*4)

def housewall(id,w,door=False):
    choices={}
    for y in range(5):
        for x in range(w):
            s='copy_base' if y==0 else 'copy_cornice' if y==4 else 'copy_plaster'
            if y in [1,2,3] and x in ([1,2,6,7] if w==9 else [2,3,7,8]): s='copy_glass'
            if x in [0,w-1]: s='copy_timber'
            if door and x==w//2 and y<3:s='copy_air'
            choices[x,y]=s
    return explicit_segment(id,w,5,choices)
hshort=housewall('house_short',9);hlong=housewall('house_long',11);hentry=housewall('house_entry',9,True)
hgeo=rectgeom('house_geometry',9,11)
h0=room('dwelling_ground',hgeo,9,11,[hentry,hlong,hshort,hlong])
h1=room('dwelling_upper',hgeo,9,11,[hshort,hlong,hshort,hlong])

st0=node('ground','STOREY','Erdgeschoss: vier Baukoerper',inputs=[hall0,t0,h0,h0],offsets=[p(),p(18,0,0),p(0,0,27),p(18,0,27)])
st1=node('upper','STOREY','Galerie und Wohnungen',inputs=[gleft,gright,gback,gnave,t1,h1,h1],offsets=[p(),p(11,0,0),p(5,0,18),p(5,0,0),p(18,0,0),p(0,0,27),p(18,0,27)])
st2=node('tower_upper','STOREY','Uhrturm Regelgeschoss',inputs=[t1],offsets=[p(18,0,0)])
st4=node('tower_top','STOREY','Uhrstube mit vier Zifferblaettern',inputs=[t4],offsets=[p(18,0,0)])
stairs=node('stairs','STAIRWELL','Offene U-Treppe 2 breit',stairwell=dict(layout='U',width=2,landingDepth=2,headroom=2,clockwise=True,enclosed=False))

rooflevels=[]
for name,x,z,w,d,storey,form,slope,upper in [
    # The hall uses a continuous 1:1 gable. Eight-plane mansards make the
    # current exact compiler's material rasterization prohibitively expensive.
    ('hall',-1,-1,17,25,'storey_1','GABLE',(1,1),(1,3)),
    ('west_house',-1,26,11,13,'storey_1','GABLE',(1,1),(1,2)),
    ('east_house',17,26,11,13,'storey_1','GABLE',(1,1),(1,2)),
    ('tower',17,-1,9,9,'storey_4','HIP',(2,1),(1,2))]:
    area=node(name+'_roof_area','AREA',name+' Dachflaeche',coordinates=[p(a,0,b) for b in range(z,z+d) for a in range(x,x+w)])
    cfg=dict(storey=storey,area=area,form=form,axis=0,mode='ANGLE',slope=dict(p=slope[0],q=slope[1]),upperSlope=dict(p=upper[0],q=upper[1]),knee16=0,overhang16=8,breakRatio=dict(p=1,q=3),family='copper' if name=='tower' else '')
    rooflevels.append(node(name+'_roof_level','ROOF_LEVEL',name+' Dach',roofLevel=cfg))
rlayout=node('roof_layout','ROOF_LAYOUT','Vier eigenstaendige Dachkoerper',inputs=rooflevels)
node('copy_copper','COPYCAT','Kupferdach',form=full,role='copper')
roof=node('roof','ROOF','Exakte Schiefer- und Kupferdaecher',assembly=True,layout=rlayout,role='roof',roofConfig=dict(family='builtin',families=[dict(id='copper',name='Kupfer',cells=[dict(slope=dict(p=2,q=1),variant=variant,phase=0,slice=row,form=copperfull) for variant in ['slope','eave','edge','ridge','hip','valley'] for row in [-1,0,1,2]],thickness16=2)],wall='copy_brick'))
# Ground-level courtyard paving stays separate from the architecture's clear space.
court=node('court','GARDEN','Gepflasterter Hof',block='minecraft:stone_bricks',coordinates=[p(x,0,z) for x in range(11,16) for z in range(25,40)],clearance=0)
paths=node('forecourt','GARDEN','Vorplatz',block='minecraft:stone',coordinates=[p(x,0,z) for x in range(-2,29) for z in range(-5,-2)],clearance=0)
access=[]
for id,x,z,to in [('hall_stairs',0,17,'storey_1'),('tower_stairs',19,2,'storey_4'),('west_stairs',1,30,'storey_1'),('east_stairs',19,30,'storey_1')]:
    access.append(dict(id=id,source=stairs,position=p(x,0,z),fromStorey='storey_0',toStorey=to,quarterTurns=0))
node('building','BUILDING','Quartier de l Horloge',levels={0:st0,1:st1,2:st2,3:st2,4:st4},roof=roof,accessInstances=access,inputs=[court,paths])

palette={'wall':'minecraft:bricks','brick':'minecraft:bricks','trim':'minecraft:smooth_sandstone','glass':'minecraft:glass','frame':'minecraft:dark_oak_planks','floor':'minecraft:spruce_planks','roof':'minecraft:deepslate_tiles','wood':'minecraft:dark_oak_planks','plaster':'minecraft:white_terracotta','copper':'minecraft:cut_copper','metal':'minecraft:gold_block','dial':'minecraft:quartz_block','stairs':'minecraft:spruce_planks','landing':'minecraft:spruce_planks','access_wall':'minecraft:bricks'}
building=dict(schema=2,id='quartier_horloge',version=1,name='Quartier de l Horloge - Galerie und Uhrturm',floors=5,roomHeight=5,wings=[dict(x=0,z=0,width=15,length=23)],palette=palette,graph=dict(schema=2,nodes=nodes))
assert len(nodes)<=96,len(nodes)
for f in forms.values():write(OUT/'forms'/f"{f['id']}.json",f)
for key,m in modules.items():write(OUT/'modules'/f"{m['id']}.json",m)
write(OUT/'building.json',building)
write(OUT/'library.json',dict(forms=forms,modules=modules,buildings={'quartier_horloge@1':building}))
write(OUT/'design-intent.json',dict(reference='https://createmod.com/de/schematics/quartier-de-lhorloge',units='Minecraft blocks; exact geometry uses sixteenths',graphNodes=len(nodes),hall=dict(interior=[15,23],storeyBases=[1,7],galleryVoid=dict(min=[5,6,0],max=[10,7,17])),tower=dict(interior=[7,7],origin=[18,0,0],storeyBases=[1,7,13,19,25]),houses=[dict(origin=[0,0,27],interior=[9,11]),dict(origin=[18,0,27],interior=[9,11])],stairs=access))
print(f'Generated {len(nodes)} graph nodes, {len(forms)} forms, {len(modules)} modules in {OUT}')
