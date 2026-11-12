"""Exercise pinned references through the actual React Flow editor and persistent browser library."""
import argparse,json
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
parser=argparse.ArgumentParser()
parser.add_argument('--url',default='http://127.0.0.1:4173/')
parser.add_argument('--chromium',default='/usr/bin/chromium-browser')
parser.add_argument('--artifacts',type=Path,default=Path('/tmp/copycat-versioned-browser'))
a=parser.parse_args();a.artifacts.mkdir(parents=True,exist_ok=True)
source={'schema':3,'name':'Ports','graph':{'schema':2,'nodes':[
 {'id':'full','kind':'INPUT','part':{'source':'copycats:copycat_block'},'inputs':[],'x':0,'y':0},
 {'id':'slab','kind':'INPUT','part':{'source':'copycats:copycat_slab'},'inputs':[],'x':0,'y':250},
 {'id':'difference','kind':'DIFFERENCE','inputs':['full','slab'],'x':300,'y':0},
 {'id':'union','kind':'UNION','inputs':['full','difference'],'x':600,'y':0},
 {'id':'result','kind':'OUTPUT','inputs':['union'],'x':900,'y':0}]}}
with sync_playwright() as p:
 browser=p.chromium.launch(executable_path=a.chromium,headless=True,args=['--no-sandbox','--enable-unsafe-swiftshader'])
 page=browser.new_page(viewport={'width':1440,'height':1000});page.set_default_timeout(20000)
 errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
 page.goto(a.url+'?editor=form')
 studio=page.locator('.studio:not([hidden])')
 with page.expect_file_chooser() as fc:studio.get_by_role('button',name='JSON öffnen',exact=True).click()
 fc.value.set_files({'name':'ports.json','mimeType':'application/json','buffer':json.dumps(source).encode()})
 studio.get_by_role('button',name='Versionierte Untergraphen',exact=True).click()
 dialog=page.get_by_role('dialog',name='Versionierte Untergraphen',exact=True);expect(dialog).to_be_visible()
 dialog.locator('.react-flow__node[data-id="full"]').click()
 dialog.locator('.react-flow__node[data-id="difference"]').click(modifiers=['Shift'])
 dialog.get_by_role('button',name='Auswahl als Untergraph veröffentlichen',exact=True).click()
 reference=dialog.locator('.react-flow__node[data-id^="ref_"]');expect(reference).to_have_count(1)
 reference.click();expect(reference.locator('.source')).to_have_count(2)
 def exported():
  with page.expect_download() as download:dialog.get_by_role('button',name='Paket exportieren',exact=True).click()
  return json.loads(Path(download.value.path()).read_text())
 first=exported()
 assert len(first['root']['shape']['nodes'])==3 and len(first['root']['references'])==1
 assert len(first['graphs'])==1 and first['root']['references'][0]['reference']['version']==1
 dialog.get_by_role('button',name='Speichern',exact=True).click()
 dialog.get_by_role('button',name='Definition dieser Version öffnen',exact=True).click()
 dialog.locator('.react-flow__node[data-id="full"]').click()
 dialog.get_by_label('Copycat-Block',exact=True).fill('copycats:copycat_slab')
 dialog.get_by_role('button',name='Neue Version veröffentlichen',exact=True).click()
 expect(dialog.locator('p[role="status"]')).to_contain_text('veröffentlicht')
 dialog.get_by_role('button',name='Zum Elterngraphen',exact=True).click()
 reference=dialog.locator('.react-flow__node[data-id^="ref_"]');reference.click()
 assert exported()['root']['references'][0]['reference']['version']==1
 select=dialog.get_by_role('combobox',name='Referenzversion wechseln',exact=True)
 versions=select.locator('option').all();assert len(versions)==3
 select.select_option(versions[-1].get_attribute('value'))
 confirm=page.get_by_role('dialog',name='Portvergleich',exact=True);expect(confirm).to_be_visible()
 confirm.get_by_role('button',name='Version übernehmen',exact=True).click()
 assert exported()['root']['references'][0]['reference']['version']==2
 dialog.get_by_role('button',name='Rückgängig',exact=True).click()
 assert exported()['root']['references'][0]['reference']['version']==1
 dialog.get_by_role('button',name='Speichern',exact=True).click()
 page.screenshot(path=str(a.artifacts/'subgraph-desktop.png'))
 dialog.get_by_role('button',name='Schließen',exact=True).click();page.reload()
 studio=page.locator('.studio:not([hidden])');studio.get_by_role('combobox',name='Versionierten Entwurf öffnen',exact=True).select_option(label='Ports')
 dialog=page.get_by_role('dialog',name='Versionierte Untergraphen',exact=True)
 expect(dialog.locator('.react-flow__node[data-id^="ref_"]')).to_have_count(1)
 for name,viewport in [('tablet',{'width':820,'height':1180}),('mobile',{'width':390,'height':844})]:
  page.set_viewport_size(viewport);page.wait_for_timeout(500)
  expect(dialog.get_by_role('button',name='Schließen',exact=True)).to_be_visible()
  page.screenshot(path=str(a.artifacts/f'subgraph-{name}.png'))
 dialog.get_by_role('button',name='In Entwurf übernehmen',exact=True).click()
 expect(dialog).to_have_count(0)
 studio=page.locator('.studio:not([hidden])')
 with page.expect_download() as download:studio.get_by_role('button',name='JSON exportieren',exact=True).click()
 executable=json.loads(Path(download.value.path()).read_text())
 assert executable['graph'] is None and executable['versionedGraph']['root']['references'][0]['reference']['version']==1
 page.reload()
 studio=page.locator('.studio:not([hidden])')
 studio.get_by_role('button',name='Versionierte Untergraphen',exact=True).click()
 dialog=page.get_by_role('dialog',name='Versionierte Untergraphen',exact=True)
 expect(dialog.locator('.react-flow__node[data-id^="ref_"]')).to_have_count(1)
 assert not errors,errors
 print(json.dumps({'passed':True,'pinnedVersions':[1,2],'namedOutputs':2,'errors':errors}))
 browser.close()
