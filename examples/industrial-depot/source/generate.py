#!/usr/bin/env python3
"""Generate the importable Lichtwerk depot using the existing building graph schema."""
import json, math
from pathlib import Path
OUT=Path(__file__).resolve().parents[1]
forms={}; nodes=[]; aliases={}; facade_cache={}
def p(x=0,y=0,z=0): return dict(x=x,y=y,z=z)
def plane(x,y,z,d): return dict(x=x,y=y,z=z,d=d)
def box(x,y,z,X,Y,Z,extra=()): return {'planes':[plane(1,0,0,X),plane(-1,0,0,-x),plane(0,1,0,Y),plane(0,-1,0,-y),plane(0,0,1,Z),plane(0,0,-1,-z),*extra]}
def part(cell,group='body'):
    if len(cell['planes'])==6:
        bounds=tuple(a['d'] for a in cell['planes'])
        if bounds==(16,0,16,0,16,0): return dict(source='copycats:copycat_block',group=group)
        if bounds==(16,0,16,0,16,-10): return dict(source='copycats:copycat_layer',size=3,tilt=3,group=group)
        if bounds==(16,0,16,0,16,-14): return dict(source='copycats:copycat_layer',size=1,tilt=3,group=group)
    return dict(source='copycats:copycat_layer',size=8,group=group,exact={'cells':[cell]})
def B(x,y,z,X,Y,Z,group='body',extra=()): return part(box(x,y,z,X,Y,Z,extra),group)
def form(id,name,parts,roles=None):
    roles=roles or {'body':'surface'}
    groups=[dict(id=k,name=k.capitalize(),role=v,priority=i) for i,(k,v) in enumerate(roles.items())]
    gnodes=[dict(id=f's{i}',kind='INPUT',part=a,x=20,y=20+i*170) for i,a in enumerate(parts)]
    if len(parts)>1: gnodes.append(dict(id='join',kind='UNION',inputs=[f's{i}' for i in range(len(parts))],x=270,y=20,materialMode='SEPARATED'))
    gnodes.append(dict(id='result',kind='OUTPUT',inputs=['join' if len(parts)>1 else 's0'],x=520,y=20))
    f=dict(schema=4,id='id_'+id,version=1,name='Lichtwerk/'+name,parts=parts,groups=groups)
    forms['id_'+id+'@1']=f
    return 'id_'+id+'@1'
def node(id,kind,name,**kw):
    n=dict(id=id,kind=kind,name=name,x=(len(nodes)//10)*330,y=(len(nodes)%10)*180,**kw);nodes.append(n);return id
def copy(id,f,role='wall',**kw): return node(id,'COPYCAT',id.replace('_',' '),form=f,role=role,**kw)
full=form('full','Bauteile/Vollblock',[B(0,0,0,16,16,16)])
wall=form('wall','Fassade/Ziegelpanel 6-16',[B(0,0,10,16,16,16)])
pier=form('pier','Fassade/Profilierter Ziegelpfeiler',[B(0,0,10,16,16,16),B(4,0,5,12,16,16,'trim'),B(6,0,2,10,16,6,'trim')],{'body':'surface','trim':'trim'})
sill=form('sill','Fassade/Steinsockel und Sohlbank',[B(0,0,10,16,16,16),B(0,0,5,16,4,16,'trim'),B(0,13,6,16,16,16,'trim')],{'body':'surface','trim':'trim'})
cornice=form('cornice','Fassade/Gestuftes Kranzgesims',[B(0,0,10,16,16,16),B(0,1,5,16,5,16,'trim'),B(0,12,2,16,16,16,'trim')],{'body':'surface','trim':'trim'})
window=form('window','Fenster/Feines Stahlsprossenfenster',[B(0,0,14,16,16,16,'glass'),B(0,0,11,1,16,16,'frame'),B(15,0,11,16,16,16,'frame'),B(0,0,11,16,1,16,'frame'),B(0,15,11,16,16,16,'frame'),B(7,0,11,8,16,16,'frame')],{'glass':'glass','frame':'frame'})
rail=form('rail','Galerie/Offene Metallbruestung',[B(0,14,10,16,16,13,'frame'),B(0,1,10,16,2,13,'frame'),B(1,0,10,2,16,13,'frame'),B(8,0,10,9,16,13,'frame'),B(15,0,10,16,16,13,'frame')],{'frame':'frame'})
stair=form('stairs','Erschliessung/Treppenstufe',[dict(source='copycats:copycat_stairs')])
corner=form('corner','Fassade/Profilierte Aussenecke',[B(8,0,8,16,16,16),B(4,0,12,8,16,16,'trim'),B(12,0,4,16,16,8,'trim')],{'body':'surface','trim':'trim'})
# Each strip is an exact planar segment of a shallow arch. Brick lies above, glass below.
arches={}
for width,hs in [(4,[4,10,13,10,4])]:
    arches[width]=[]
    for j,(h0,h1) in enumerate(zip(hs,hs[1:])):
        dy=h1-h0
        under=plane(-dy,16,0,16*h0)
        above=plane(dy,-16,0,-16*(h0+2))
        band=[plane(-dy,16,0,16*(h0+2)),plane(dy,-16,0,-16*h0)]
        f=form(f'arch_{width}_{j}',f'Fenster/Segmentbogen {width}-{j+1}',[
            B(0,0,14,16,16,16,'glass',[under]),B(0,0,8,16,16,16,'trim',band),B(0,0,10,16,16,16,'body',[above])],{'glass':'glass','body':'surface','trim':'trim'})
        arches[width].append(f)
node('empty_wall','SEGMENT','Leere Wand',block='minecraft:air',length=1,module='')
copy('brick',wall);copy('pilaster',pier);copy('base',sill);copy('cornice',cornice);copy('window',window,'glass');copy('concrete',full,'floor');copy('corner',corner);copy('rail',rail,'frame');copy('stairs',stair,'frame')
for w,fs in arches.items():
    for j,f in enumerate(fs): copy(f'a{w}{j}',f)
# A fully explicit cell layout gives every facade bay an editable source assignment.
sources=['brick','pilaster','base','cornice','window','rail']+[f'a{w}{j}' for w in [4] for j in range(w)]
def facade(id,length,bay=6,mode='window',height=5):
    key=(length,bay,mode,height)
    if key in facade_cache:
        aliases[id]=facade_cache[key];return facade_cache[key]
    if mode=='window' and length in [28,36]:
        unit=facade('bay_6',6,6,mode,height)
        facade_cache[key]=id
        if length==28:
            if not any(n['id']=='end_pier' for n in nodes): node('end_pier','SEGMENT','Doppelter Randpfeiler',module='',length=2,height=5,form=pier)
            return node(id,'PATTERN',id,inputs=['end_pier']+[unit]*4+['end_pier'])
        return node(id,'PATTERN',id,inputs=[unit]*6)
    if mode=='canopy' and length==36:
        unit=facade('canopy_bay',6,6,mode,height)
        facade_cache[key]=id
        return node(id,'PATTERN',id,inputs=[unit]*6)
    facade_cache[key]=id
    cells=[]
    for y in range(height):
      for x in range(length):
        active_bay=6 if bay==7 else bay
        bx=(x-2)%6 if bay==7 else x%bay
        outside=bay==7 and (x<2 or x>=length-2)
        if mode=='open':
            # The gallery edge is physically open towards the tall hall.
            source='pilaster' if x<2 or x>=length-2 else ('rail' if y==0 else 'air')
        elif mode=='hall_open': source='pilaster' if x<2 or x>=length-2 else 'air'
        elif mode=='canopy': source='pilaster' if bx==0 else 'air'
        elif mode=='canopy_side': source='pilaster' if x in [0,length-1] else 'air'
        else:
            source='base' if y==0 else 'cornice' if y==height-1 else 'pilaster' if (outside or bx in [0,active_bay-1]) else 'window'
            # Top glass row is an exact arch; the cornice remains a separate row.
            if y==height-2 and not outside and bx not in [0,active_bay-1] and bay in [6,7]: source=f'a4{bx-1}'
            if mode=='personnel' and x in [4,5] and y<3: source='air'
            if mode=='loading' and x//bay in [1,3,4] and bx in range(1,bay-1) and y<height-1: source='air'
            if mode=='passage' and 12<=x<=15 and y<4: source='air'
        if source!='air': cells.append(dict(x=x,y=y,sourceIndex=sources.index(source)))
    layout=node(id+'_layout','SEGMENT_LAYOUT',id+' Raster',length=length,height=height,layoutCells=cells)
    return node(id,'SEGMENT',id,assembly=True,module='',layout=layout,inputs=sources)
# 16 input limit: the shared facade table contains exactly 16 Copycat sources.
assert len(sources)==10
facade('hall_north_low',36,6,'loading');facade('hall_36',36,6);facade('hall_28',28,7);facade('hall_east_low',28,7,'passage')
facade('hall_east_open',28,7,'hall_open');facade('office_10',10,10);facade('office_28',28,7);facade('office_west_low',28,7,'passage');facade('gallery_edge',28,7,'open')
facade('office_entrance',10,10,'personnel')
facade('canopy_front',36,6,'canopy');facade('canopy_side',5,5,'canopy_side')
node('floor','FLOOR','Betonboden',inputs=['concrete'])
node('office_ceiling','CEILING','Geschossplatte Galerie',inputs=['concrete'])
node('open_ceiling','CEILING','Halle nach oben offen',block='minecraft:air',form=full,occupied=True)
# Horizontal segment: opaque ring only, transparent interior. No artificial flat ceiling closes the hall.
ring=[]
for z in range(30):
    for x in range(38):
        if x in [0,37] or z in [0,29]: ring.append(dict(x=x,y=z,sourceIndex=0))
node('hall_ring_layout','SEGMENT_LAYOUT','Hallenrand 38 x 30 mit freier Mitte',length=38,height=30)
node('hall_ring','SEGMENT','Hallenrand auf Galeriehoehe',assembly=True,module='',layout='hall_ring_layout',regionCopies={f'region:x{x}_y{y}_zb':'brick' for x in ['0','i','1'] for y in ['0','i','1'] if (x,y)!=('i','i')})
# Room edges use the repository's canonical clockwise boundary IDs.
def room(id,width,depth,walls,floor='floor',ceiling='office_ceiling',xoff=0,zoff=0,height=5):
    rects=[dict(x=x,z=0,width=min(32,width-x),length=depth) for x in range(0,width,32)]
    return node(id,'ROOM',id.replace('_',' '),height=height,rectangles=rects,walls={f'0:0:0':walls[0],f'1:{width-1}:0':walls[1],f'2:{width-1}:{depth-1}':walls[2],f'3:0:{depth-1}':walls[3]},floor=floor,ceiling=ceiling,corner='corner')
# Ring segments require assembly=True so ROOM accepts segment floor/ceiling, plus geometry.
hall_cells=[p(x,0,z) for z in range(28) for x in range(36)]
node('hall_geometry','ROOM_GEOMETRY','Halle 36 x 28',height=5,coordinates=hall_cells)
room('hall_ground',36,28,['hall_north_low','hall_east_low','hall_36','hall_28'],ceiling='hall_ring')
nodes[-1].update(assembly=True,geometry='hall_geometry')
room('hall_upper',36,28,['hall_36','hall_east_open','hall_36','hall_28'],floor='hall_ring',ceiling='open_ceiling')
nodes[-1].update(assembly=True,geometry='hall_geometry')
room('office_ground',10,28,['office_10','office_28','office_entrance','office_west_low'])
room('gallery_upper',10,28,['office_10','office_28','office_10','gallery_edge'],ceiling='open_ceiling')
room('covered_loading',36,5,['canopy_front','canopy_side','canopy_front','canopy_side'])
# Canopy is open; all upper-column corners remain actual brick pilasters.
node('ground','STOREY','Erdgeschoss und ueberdachte Laderampe',inputs=['hall_ground','office_ground','covered_loading'],offsets=[p(),p(38,0,0),p(0,0,-7)])
node('upper','STOREY','Galerie und obere Hallenfassade',inputs=['hall_upper','gallery_upper'],offsets=[p(),p(38,0,0)])
node('stairwell','STAIRWELL','Offene U-Treppe zur Galerie',stairwell=dict(layout='U',width=2,landingDepth=2,headroom=3,enclosed=False,tread='stairs',landing='concrete'))
# Exact roofs are generated by the existing Roofs compiler. Hall segments share the same span and pitch.
rooflevels=[]
def roofarea(id,xs,zs,storey,shape='GABLE',family='metal'):
    area=node(id+'_area','AREA',id+' Grundflaeche',coordinates=[p(x,0,z) for z in zs for x in xs])
    r=node(id,'ROOF_LEVEL',id,roofLevel=dict(storey=storey,area=area,form=shape,axis=0,slope=dict(p=1,q=4),upperSlope=dict(p=1,q=2),overhang16=0,knee16=0,family=family))
    rooflevels.append(r)
roofarea('hall_roof_south',range(-1,37),range(23,29),'upper')
roofarea('hall_skylight_2',range(-1,37),range(20,23),'upper',family='glass')
roofarea('hall_roof_middle',range(-1,37),range(14,20),'upper')
roofarea('hall_skylight',range(-1,37),range(11,14),'upper',family='glass')
roofarea('hall_roof_north',range(-1,37),range(-1,11),'upper')
roofarea('office_roof',range(37,49),range(-1,29),'upper','FLAT')
roofarea('canopy_roof',range(-1,37),range(-8,-1),'ground','FLAT')
node('roof_layout','ROOF_LAYOUT','Dachplan mit echtem Glaslichtband',inputs=rooflevels)
glassfull=form('roof_glass_full','Dach/Glaslichtband-Quelle',[B(0,0,0,16,16,16,'glass')],{'glass':'glass'})
roofvariants=['slope','eave','edge','ridge','hip','valley']
def roofcells(f): return [dict(slope=dict(p=1,q=4),variant=v,phase=phase,slice=sl,form=f) for v in roofvariants for phase in range(4) for sl in [-1,0]]
node('roof','ROOF','Exaktes Metalldach und Glaslichtband',assembly=True,layout='roof_layout',role='roof',roofConfig=dict(family='metal',wall='brick',families=[dict(id='metal',name='Gefalztes Metalldach',cells=[],slopes=[dict(p=1,q=4)],thickness16=2),dict(id='glass',name='Glaslichtband',cells=roofcells(glassfull),slopes=[dict(p=1,q=4)],thickness16=2)]))
copy('entrance_stair',stair,'floor',quarterTurns=2)
node('entrance_steps','PATH','Zweistufiger Personeneingang',coordinates=[p(42,0,29),p(43,0,29)],floor='entrance_stair',block='minecraft:stone',pathWidth=1,groundDepth=1,elevation=0)
node('building','BUILDING','Lichtwerk Industrie-Depot',inputs=['entrance_steps'],offsets=[p()],levels={'0':'ground','1':'upper'},levelIds={'0':'ground','1':'upper'},roof='roof',accessInstances=[dict(id='gallery_stairs',source='stairwell',position=p(41,0,9),fromStorey='ground',toStorey='upper')])
building=dict(schema=2,id='industrial_depot_lichtwerk',version=1,name='Lichtwerk - Industrie-Depot mit offener Galerie',wings=[dict(x=0,z=0,width=32,length=28),dict(x=32,z=0,width=16,length=28)],floors=2,roomHeight=5,floorForm=full,ceilingForm=full,trimForm=corner,palette=dict(wall='minecraft:bricks',trim='minecraft:polished_andesite',frame='minecraft:polished_blackstone',glass='minecraft:glass',floor='minecraft:smooth_stone',roof='minecraft:deepslate_tiles',gable='minecraft:bricks',roof_ridge='minecraft:deepslate_tiles',roof_hip='minecraft:deepslate_tiles',roof_valley='minecraft:deepslate_tiles',roof_transition='minecraft:deepslate_tiles'),graph=dict(schema=2,nodes=nodes))
for n in nodes:
    if 'inputs' in n: n['inputs']=[aliases.get(a,a) for a in n['inputs']]
    if 'walls' in n: n['walls']={k:aliases.get(v,v) for k,v in n['walls'].items()}
assert len(nodes)<=96,len(nodes)
OUT.mkdir(exist_ok=True)
(OUT/'forms').mkdir(exist_ok=True)
for obsolete in (OUT/'forms').glob('id_arch_5_*.json'): obsolete.unlink()
for f in forms.values(): (OUT/'forms'/f"{f['id']}.json").write_text(json.dumps(f,indent=2)+'\n')
(OUT/'building.json').write_text(json.dumps(building,separators=(',',':'))+'\n')
(OUT/'library.json').write_text(json.dumps(dict(forms=forms,modules={},buildings={building['id']+'@1':building}),indent=2)+'\n')
print(f'Generated {len(nodes)} building nodes, {len(forms)} forms in {OUT}')
