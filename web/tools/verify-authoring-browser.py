from icon_select import choose, option_labels
import json,argparse,re
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
parser=argparse.ArgumentParser();parser.add_argument('--url',default='http://127.0.0.1:4320/');parser.add_argument('--artifacts',type=Path,default=Path('/tmp/graphstudio-authoring-browser-check'));args=parser.parse_args();args.artifacts.mkdir(parents=True,exist_ok=True)
fixture=Path(__file__).resolve().parents[2]/'examples/building-graph-assembly.json'
doc=json.loads(fixture.read_text());doc['name']='Segmentraster-Prüfung';doc['graph']['schema']=11;doc['palette']={'wall':'minecraft:stone','roof':'minecraft:deepslate_tiles','gable':'minecraft:oak_planks','frame':'minecraft:oak_planks','glass':'minecraft:glass','trim':'minecraft:stone_bricks'}
base={'kind':'SEGMENT','module':'','form':'full@1','assembly':False,'length':3,'height':2,'depth':1,'x':100,'y':-250}
doc['graph']['nodes'] += [{**base,'id':'operand_a','name':'Segment A'}, {**base,'id':'operand_b','name':'Segment B','length':2,'x':450}, {'id':'op','kind':'SEGMENT_OPERATOR','operation':'INTERSECTION','name':'Schnitt prüfen','inputs':['operand_a','operand_b'],'operandOffsets':[{'x':0,'y':0,'z':0},{'x':2,'y':0,'z':0}],'x':800,'y':-250}]
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path='/usr/bin/chromium-browser',headless=True,args=['--no-sandbox','--enable-unsafe-swiftshader'])
 context=browser.new_context(viewport={'width':1440,'height':1000});page=context.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)));page.set_default_timeout(20000)
 page.goto(args.url,wait_until='networkidle',timeout=60000)
 def library():
  page.locator('.graph-heading').get_by_role('button',name='Bibliothek',exact=True).click()
  return page.get_by_role('dialog',name='Bibliothek',exact=True)
 def close(dialog):dialog.locator('header > button[aria-label="Schließen"]').click()
 def export():
  dialog=library()
  with page.expect_download() as download:dialog.get_by_role('button',name='Export …',exact=True).click()
  value=json.loads(Path(download.value.path()).read_text());close(dialog);return value
 dialog=library()
 with page.expect_file_chooser() as chosen:dialog.get_by_role('button',name='Import …',exact=True).click()
 chosen.value.set_files({'name':'segments.json','mimeType':'application/json','buffer':json.dumps(doc).encode()})
 expect(page.get_by_role('dialog')).to_have_count(0)
 page.locator('.react-flow__node[data-id="op"] strong').click()
 expect(page.get_by_role('region',name='2D-Segmentanordnung')).to_be_visible()
 expect(page.get_by_role('application',name='2D-Anordnungsraster')).to_be_visible()
 expect(page.locator('.side > section')).to_have_count(3)
 header=page.locator('.graph-heading')
 expect(header.get_by_role('button',name='Knoten löschen',exact=True)).to_be_enabled()
 before=export()
 header.get_by_role('button',name='Knoten löschen',exact=True).click()
 assert not any(n['id']=='op' for n in export()['graph']['nodes'])
 header.get_by_role('button',name='Rückgängig',exact=True).click()
 assert any(n['id']=='op' for n in export()['graph']['nodes'])
 header.get_by_role('button',name='Wiederholen',exact=True).click()
 assert not any(n['id']=='op' for n in export()['graph']['nodes'])
 header.get_by_role('button',name='Rückgängig',exact=True).click()
 page.locator('.react-flow__node[data-id="op"] strong').click()
 page.locator('.preview-panel').first.get_by_role('button',name='Selektiert',exact=True).click()
 panel=page.get_by_role('region',name='2D-Segmentanordnung')
 choose(panel.get_by_role('combobox',name='Anordnungseingang',exact=True),'1')
 expect(panel.get_by_role('spinbutton',name='Anordnung X',exact=True)).to_have_value('2')
 panel.get_by_role('spinbutton',name='Anordnung X',exact=True).fill('4')
 # Live draft makes the intersection empty, without persisting or consuming an undo step.
 expect(page.locator('.preview-meta[role="status"]')).to_contain_text('0 Platzierungen',timeout=30000)
 panel.get_by_role('button',name='Verwerfen',exact=True).click()
 expect(panel.get_by_role('spinbutton',name='Anordnung X',exact=True)).to_have_value('2')
 panel.get_by_role('spinbutton',name='Anordnung X',exact=True).fill('1')
 panel.get_by_role('button',name='Anordnung übernehmen',exact=True).click()
 assert next(n for n in export()['graph']['nodes'] if n['id']=='op')['operandOffsets'][1]['x']==1
 header.get_by_role('button',name='Rückgängig',exact=True).click()
 assert next(n for n in export()['graph']['nodes'] if n['id']=='op')['operandOffsets'][1]['x']==2
 page.locator('.react-flow__node[data-id="op"] strong').click()
 # Material selection uses the shared dialog and never changes the draft on cancel.
 summary=page.locator('.document-fields > summary');summary.click()
 page.locator('.material-field').first.get_by_role('button',name=re.compile('wählen')).click()
 material=page.get_by_role('dialog',name=re.compile('Material wählen'))
 expect(material.locator('.material-list .block-inventory-icon').first).to_be_visible()
 material.get_by_role('searchbox').fill('minecraft:oak_log')
 material.locator('.material-list button').filter(has_text='minecraft:oak_log').click()
 expect(material.get_by_role('combobox',name='Blockzustand axis',exact=True)).to_be_visible()
 choose(material.get_by_role('combobox',name='Blockzustand axis',exact=True),'x')
 material.get_by_role('button',name='Abbrechen',exact=True).click()
 assert export()['palette']==before['palette']
 # Reopen, apply and check the palette's block-only contract.
 page.locator('.material-field').first.get_by_role('button',name=re.compile('wählen')).click()
 material=page.get_by_role('dialog',name=re.compile('Material wählen'))
 material.get_by_role('searchbox').fill('minecraft:oak_log')
 material.locator('.material-list button').filter(has_text='minecraft:oak_log').click()
 expect(material.get_by_role('button',name='Dieses Material verwenden',exact=True)).to_be_enabled()
 material.get_by_role('button',name='Dieses Material verwenden',exact=True).click()
 after=export();assert list(after['palette'].values())[0]=='minecraft:oak_log'
 for width,height,label in [(1440,1000,'desktop'),(820,1180,'tablet'),(390,844,'mobile')]:
  page.set_viewport_size({'width':width,'height':height})
  page.locator('.react-flow__controls-fitview').click()
  page.locator('.react-flow__node[data-id="op"] strong').click()
  expect(page.get_by_role('region',name='2D-Segmentanordnung')).to_be_visible()
  actions=page.locator('.graph-actions').bounding_box();assert actions['x']>=0 and actions['x']+actions['width']<=width+1
  page.locator('.arrangement-editor').evaluate('(el)=>el.scrollTop=0')
  page.locator('.preview-panel').first.get_by_role('button',name='3D einpassen',exact=True).click()
  page.screenshot(path=str(args.artifacts/f'segment-raster-{label}.png'),full_page=True)
  # Native dialog focus isolation and Escape cancellation work in each viewport.
  page.locator('.document-fields > summary').evaluate('(el)=>el.parentElement.open=true')
  page.locator('.material-field').first.get_by_role('button',name=re.compile('wählen')).click()
  material=page.get_by_role('dialog',name=re.compile('Material wählen'))
  page.keyboard.press('Tab');assert material.evaluate('(el)=>el.contains(document.activeElement)')
  rect=material.bounding_box();assert rect['x']>=0 and rect['x']+rect['width']<=width+1
  page.screenshot(path=str(args.artifacts/f'material-dialog-{label}.png'),full_page=True)
  page.keyboard.press('Escape');expect(material).not_to_be_visible()
 # New roofs have direct surface ports and editable section settings, while converter nodes are hidden.
 page.set_viewport_size({'width':1440,'height':1000})
 header.get_by_role('button',name='+ Knoten',exact=True).click()
 picker=page.get_by_role('dialog',name='Knoten hinzufügen',exact=True)
 for old_name in ('Segmentanordnung auswählen','Raumgrundriss auswählen','Dachebene auswählen','Dachanordnung auswählen'):
  expect(picker.get_by_role('button',name=old_name,exact=True)).to_have_count(0)
 picker.get_by_role('searchbox').fill('Dach')
 picker.get_by_role('article',name='Vorschau · Dach',exact=True).click()
 picker.get_by_role('button',name='Ausgewählten Knoten in Kartenmitte einfügen',exact=True).click()
 expect(page.locator('[data-inspector-field="roofSections"]')).to_be_visible()
 assert next(n for n in export()['graph']['nodes'] if n.get('roofSections'))['roofSections'][0]['id']=='section_1'
 sections=page.locator('[data-inspector-field="roofSections"]')
 sections.get_by_role('button',name='+ Eintrag',exact=True).last.click()
 page.locator('.native-fields').get_by_role('button',name='Übernehmen',exact=True).click()
 roofs=[n for n in export()['graph']['nodes'] if n.get('roofSections')]
 assert len(roofs[-1]['roofSections'])==2
 page.screenshot(path=str(args.artifacts/'direct-roof-settings-desktop.png'),full_page=True)
 assert not errors,errors
 browser.close()
print('Graphstudio authoring browser checks passed: header deletion/undo/redo, live orthographic positioning, material cancel/apply, focus, desktop/tablet/mobile.')
