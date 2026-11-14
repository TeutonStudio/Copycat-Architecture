"""Exercise inspector scrolling, integer counts and storey windows in Chromium."""
import argparse,json
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
p=argparse.ArgumentParser();p.add_argument('--url',default='http://127.0.0.1:4194/');p.add_argument('--chromium',required=True);p.add_argument('--root',default=str(Path(__file__).resolve().parents[2]));a=p.parse_args();root=Path(a.root)
with sync_playwright() as runtime:
 b=runtime.chromium.launch(executable_path=a.chromium,headless=True,args=['--no-sandbox','--enable-unsafe-swiftshader'])
 page=b.new_page(viewport={'width':1600,'height':1100});page.set_default_timeout(20000);errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
 page.goto(a.url+'?editor=building');studio=page.locator('.studio:not([hidden])')
 def snapshot():
  expect(page.locator('.workspace-status')).to_contain_text('Im Browser gespeichert')
  return page.evaluate("""()=>new Promise(resolve=>{const r=indexedDB.open('copycat-architect-graphstudio',1);r.onsuccess=()=>{const q=r.result.transaction('workspace').objectStore('workspace').get('current');q.onsuccess=()=>{resolve(q.result);r.result.close()}}})""")
 def current():return snapshot()['drafts']['building']['document']
 def tools_control(control):
  tools=studio.locator('.studio-tools');panel=tools.locator('.paged-panel')
  if not tools.get_attribute('open') is not None:tools.locator(':scope > summary').click()
  back=panel.locator(':scope > .page-controls button').first
  for _ in range(100):
   if back.is_disabled():break
   back.click()
  for _ in range(100):
   if control.is_visible():return control
   next=panel.locator(':scope > .page-controls button').nth(1)
   if next.is_disabled():break
   next.click()
  raise AssertionError('unreachable tools control')
 def close_tools():
  if studio.locator('.studio-tools').get_attribute('open') is not None:studio.locator('.studio-tools > .paged-panel > .page-controls').get_by_role('button',name='Schließen',exact=True).click()
 def history(label):tools_control(studio.get_by_role('button',name=label,exact=True)).click();close_tools()
 def load(file):
  studio.locator('.studio-tools input[type=file]').first.set_input_files(str(root/'examples'/file));expect(studio.locator('.canvas-footer')).to_contain_text('Knoten')
  page.wait_for_timeout(500)
 def select(id):
  tabs=studio.get_by_role('group',name='Arbeitsansicht')
  if tabs.is_visible():tabs.get_by_role('button',name='Graph',exact=True).click()
  studio.locator(f'.react-flow__node[data-id="{id}"]').click()
  if tabs.is_visible():tabs.get_by_role('button',name='Eigenschaften',exact=True).click()
 def apply():studio.locator('.inspector-body > .native-fields > button.primary').first.click()
 def getnode(id):return next(n for n in current()['graph']['nodes'] if n['id']==id)
 load('building-rotation-segment.json');select('column')
 studio.get_by_role('button',name='Quelle bearbeiten',exact=True).click()
 value=studio.locator('[data-inspector-field=countValue] input[type=number]');expect(value).to_have_value('6')
 assert studio.locator('[data-inspector-field=countValue] input[type=checkbox]').count()==0
 value.fill('9');apply();assert getnode('count')['countValue']==9
 value.fill('-1');expect(value).to_have_value('9');value.fill('1.5');expect(value).to_have_value('9')
 select('column');expect(studio.locator('[data-inspector-field=length] output')).to_have_text('9')
 history('Rückgängig');expect(studio.locator('[data-inspector-field=length] output')).to_have_text('6')
 studio.get_by_label('Länge (Y)',exact=True).select_option('');studio.get_by_role('button',name='Quelle bearbeiten',exact=True).count()
 select('count');value.fill('0');apply();assert getnode('count')['countValue']==0
 load('building-windows.json');select('storey')
 panel=studio.locator('.inspector-body');assert panel.locator(':scope > .page-controls').count()==0
 window=studio.get_by_label('Position entlang der Wand 1',exact=True);window.scroll_into_view_if_needed();window.fill('3');apply();assert getnode('storey')['windows'][0]['offset']==3
 history('Rückgängig');assert getnode('storey')['windows'][0]['offset']==1
 history('Wiederholen');assert getnode('storey')['windows'][0]['offset']==3
 plan=studio.get_by_role('application',name='Fenstergrundriss',exact=True);plan.scroll_into_view_if_needed();plan.focus();plan.press('ArrowLeft');apply();assert getnode('storey')['windows'][0]['offset']==2
 # Click a different wall, then drag the active window along it.
 plan.scroll_into_view_if_needed();south=plan.locator(':scope > g').nth(2)
 south.locator('rect').nth(1).click();apply();assert getnode('storey')['windows'][0]['edge'].startswith('2:')
 plan.scroll_into_view_if_needed();start=plan.locator(':scope > g').nth(4).locator('rect').first.bounding_box();target=south.locator('rect').nth(3).bounding_box()
 page.mouse.move(start['x']+start['width']/2,start['y']+start['height']/2);page.mouse.down();page.mouse.move(target['x']+target['width']/2,target['y']+target['height']/2,steps=8);page.mouse.up();apply()
 assert getnode('storey')['windows'][0]['offset']==3
 plan.scroll_into_view_if_needed();page.screenshot(path='/tmp/graphstudio-window-groundplan.png')
 before=getnode('storey')['windows'];page.reload();select('storey');assert getnode('storey')['windows']==before
 studio.get_by_label('Einbauhöhe 1',exact=True).fill('7');apply()
 # Invalid geometry stays editable and is explained by the shared preview compiler.
 studio.get_by_role('group',name='Vorschauziel',exact=True).get_by_role('button',name='Selektiert',exact=True).click()
 expect(studio.locator('.preview-meta[role=status]')).to_contain_text('Stockwerkshöhe')
 history('Rückgängig');expect(studio.locator('.preview-meta[role=status]')).to_contain_text('Zinkeinheiten')
 for w,h in [(1600,1100),(1366,768),(390,844),(844,390),(320,568)]:
  page.set_viewport_size({'width':w,'height':h})
  tabs=studio.get_by_role('group',name='Arbeitsansicht')
  if tabs.is_visible():tabs.get_by_role('button',name='Eigenschaften',exact=True).click()
  panel.evaluate('(e)=>{e.scrollTop=e.scrollHeight}')
  assert panel.evaluate('(e)=>e.scrollHeight>e.clientHeight && e.scrollTop>0')
  assert page.evaluate('document.documentElement.scrollWidth===innerWidth && document.documentElement.scrollHeight===innerHeight && scrollX===0 && scrollY===0')
  assert panel.evaluate('(e)=>e.scrollWidth<=e.clientWidth'),(w,h,'horizontal inspector overflow')
  if w in [1600,390]:page.screenshot(path=f'/tmp/graphstudio-corrections-{w}.png')
 assert not errors,errors
 print('PASS: counts 0/positive/integer validation, live column, scrolling inspector, fixed viewport, window positions, Undo/Redo, reload and placement errors')
 b.close()
