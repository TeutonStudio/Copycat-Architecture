import json,re,argparse
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
form={'schema':3,'name':'UI-Prüfform','groups':[{'id':'body','name':'Body','role':'surface','priority':0}], 'graph':{'schema':2,'nodes':[
 {'id':'source','kind':'INPUT','part':{'source':'copycats:copycat_slab','group':'body'},'x':0,'y':0},
 {'id':'output','kind':'OUTPUT','inputs':['source'],'x':300,'y':0}]}}
parser=argparse.ArgumentParser(description='Graphstudio browser smoke test (Playwright + Chromium).')
parser.add_argument('--url',default='http://127.0.0.1:5173/')
parser.add_argument('--chromium',default='/usr/bin/chromium')
parser.add_argument('--artifacts',type=Path,default=Path('/tmp/graphstudio-browser-check'))
args=parser.parse_args();args.artifacts.mkdir(parents=True,exist_ok=True)
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path=args.chromium,headless=True,args=['--no-sandbox','--enable-unsafe-swiftshader'])
 page=browser.new_page(viewport={'width':1440,'height':1000})
 page.set_default_timeout(20000)
 errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
 page.goto(args.url.rstrip('/')+'/?editor=form');page.wait_for_timeout(1000)
 studio=page.locator('.studio:not([hidden])')
 def import_file(button,data,name):
  with page.expect_file_chooser() as fc:studio.get_by_role('button',name=button,exact=True).click()
  fc.value.set_files({'name':name,'mimeType':'application/json','buffer':json.dumps(data).encode()})
  page.wait_for_timeout(500)
 def download():
  with page.expect_download() as d:studio.get_by_role('button',name='JSON exportieren',exact=True).click()
  return json.loads(Path(d.value.path()).read_text())
 def open_picker():
  studio.locator('.toolbar').get_by_role('button',name='Copycat auswählen',exact=True).click()
  dlg=page.get_by_role('dialog',name='Copycat auswählen');expect(dlg).to_be_visible();return dlg
 import_file('JSON öffnen',form,'test-form.json')
 page.wait_for_timeout(1000)
 studio.locator('.react-flow__node[data-id="source"]').click()
 # Import/remount and parent renders must retain measured node dimensions.
 for _ in range(3):
  import_file('JSON öffnen',form,'test-form.json')
  expect(studio.locator('.react-flow__node[data-id="source"]')).to_be_visible()
  studio.locator('.react-flow__node[data-id="source"]').click()
  studio.get_by_label('Name für Bibliothek',exact=True).fill('Messprüfung')
  expect(studio.locator('.react-flow__node[data-id="source"]')).to_be_visible()
  expect(studio.locator('.react-flow__node[data-id="output"]')).to_be_visible()
 original=download()
 assert all('measured' not in n for n in original['graph']['nodes'])
 dialog=open_picker();expect(dialog.locator('.copycat-card')).to_have_count(19)
 expect(dialog.locator('.copycat-thumbnail img').first).to_be_visible(timeout=30000)
 # Search and real geometry preview, cancel and Escape never mutate the source.
 dialog.get_by_role('searchbox').fill('Dachvorlage')
 card=dialog.get_by_role('button',name='Dachvorlage · Grundform',exact=True);card.click()
 expect(dialog.get_by_role('button',name='Auswahl übernehmen',exact=True)).to_be_enabled(timeout=30000)
 expect(dialog.locator('.copycat-preview-stage canvas')).to_be_visible()
 page.screenshot(path=str(args.artifacts/'copycat-picker-desktop.png'),full_page=True)
 page.keyboard.press('Escape');expect(dialog).not_to_be_visible()
 assert download()==original
 dialog=open_picker();dialog.get_by_role('searchbox').fill('Stufe');dialog.get_by_role('button',name='Stufe · Grundform',exact=True).click()
 expect(dialog.get_by_role('button',name='Auswahl übernehmen',exact=True)).to_be_enabled()
 dialog.get_by_role('button',name='Auswahl übernehmen',exact=True).click();expect(dialog).not_to_be_visible()
 # Save a named graph, then use it in the building editor.
 studio.get_by_label('Name für Bibliothek',exact=True).fill('UI-Prüfform')
 studio.get_by_role('button',name='Graph speichern',exact=True).click()
 page.get_by_role('button',name='Gebäudegraph',exact=True).click()
 expect(studio.get_by_role('combobox',name='Knotentyp',exact=True)).to_be_visible()
 options=studio.get_by_role('combobox',name='Knotentyp',exact=True).locator('option').all_text_contents()
 for family in ['Copycat','Segment']:
  for name in (['Copycat rotieren','Copycat spiegeln','Copycat-Vereinigung','Copycat-Schnitt','Copycat-Differenz','Symmetrische Copycat-Differenz'] if family=='Copycat' else ['Segment rotieren','Segment spiegeln','Segment-Vereinigung','Segment-Schnitt','Segment-Differenz','Symmetrische Segment-Differenz']):
   assert name in options,name
 before=download()
 dialog=open_picker();dialog.get_by_role('tab',name=re.compile('Gespeicherte Formen')).click()
 dialog.get_by_role('searchbox').fill('UI-Prüfform')
 dialog.get_by_role('button',name='UI-Prüfform · Meine gespeicherten Formen',exact=True).click()
 expect(dialog.get_by_role('button',name='Auswahl übernehmen',exact=True)).to_be_enabled(timeout=30000)
 dialog.get_by_role('button',name='Auswahl übernehmen',exact=True).click();expect(dialog).not_to_be_visible()
 after=download();assert len(after['graph']['nodes'])==len(before['graph']['nodes'])+1
 added=next(n for n in after['graph']['nodes'] if n['id'] not in {v['id'] for v in before['graph']['nodes']})
 assert added['kind']=='COPYCAT' and '@' in added['form']
 studio.get_by_role('button',name='Rückgängig',exact=True).click();assert download()==before
 studio.get_by_role('button',name='Wiederholen',exact=True).click();assert download()==after
 page.wait_for_timeout(800);page.reload();page.wait_for_timeout(1000);assert download()==after
 # Broken library geometry must be visible as an error and cannot be applied.
 import_file('Bibliothek öffnen',{'forms':{'invalid@1':{'schema':3,'name':'Ungültige Prüfform','parts':[{'source':'unknown:invalid'}]}}},'invalid-library.json')
 dialog=open_picker();dialog.get_by_role('tab',name=re.compile('Gespeicherte Formen')).click();dialog.get_by_role('searchbox').fill('Ungültige')
 dialog.get_by_role('button',name=re.compile('Ungültige Prüfform')).click()
 expect(dialog.locator('.copycat-preview-placeholder .error')).to_be_visible(timeout=30000)
 expect(dialog.get_by_role('button',name='Auswahl übernehmen',exact=True)).to_be_disabled()
 dialog.get_by_role('button',name='Abbrechen',exact=True).click()
 # Desktop, tablet and mobile show real thumbnails and keep focus inside the dialog.
 for width,height,label in [(820,1180,'tablet'),(390,844,'mobile')]:
  page.set_viewport_size({'width':width,'height':height});dialog=open_picker()
  dialog.get_by_role('tab',name=re.compile('Grundformen')).click()
  dialog.get_by_role('searchbox').fill('Treppe');dialog.get_by_role('button',name='Treppe · Grundform',exact=True).click()
  expect(dialog.get_by_role('button',name='Auswahl übernehmen',exact=True)).to_be_enabled(timeout=30000)
  expect(dialog.locator('.copycat-thumbnail img').first).to_be_visible(timeout=30000)
  page.keyboard.press('Tab');assert page.evaluate('document.querySelector("dialog[open]").contains(document.activeElement)')
  rect=dialog.bounding_box();assert rect['x']>=0 and rect['x']+rect['width']<=width+1
  page.screenshot(path=str(args.artifacts/f'copycat-picker-{label}.png'),full_page=True)
  page.keyboard.press('Escape');expect(dialog).not_to_be_visible()
 assert not errors,errors
 browser.close()
print('Complete Graphstudio browser checks passed: 19 shapes/3D thumbnails, preview, cancel/Escape, saved forms, 12 operators, undo/redo, reload, invalid source rejection, desktop/tablet/mobile, focus isolation.')
