"""Exercise SVG choices, keyboard commits, modal layering and real graph integration."""
import argparse,json,re,shutil
from pathlib import Path
from playwright.sync_api import sync_playwright,expect
from icon_select import choose

parser=argparse.ArgumentParser()
parser.add_argument('--url',default='http://127.0.0.1:4326/')
parser.add_argument('--chromium',default=shutil.which('chromium') or shutil.which('chromium-browser'))
parser.add_argument('--artifacts',type=Path,default=Path('/tmp/graphstudio-dropdown-icons-evidence'))
args=parser.parse_args();args.artifacts.mkdir(parents=True,exist_ok=True)
with sync_playwright() as p:
    browser=p.chromium.launch(executable_path=args.chromium,headless=True,args=['--no-sandbox','--enable-unsafe-swiftshader'])
    page=browser.new_page(viewport={'width':1440,'height':1000});page.set_default_timeout(15000)
    errors=[];page.on('pageerror',lambda error:errors.append(str(error)))
    page.goto(args.url.rstrip('/')+'/test/fixtures/dropdown-icons.html')
    control=page.get_by_role('combobox',name='Testauswahl',exact=True)
    def result():return json.loads(page.locator('#result').inner_text())
    expect(control).to_be_visible();assert result()=={'value':0,'type':'number','commits':0}
    control.click();page.keyboard.press('ArrowDown');page.keyboard.press('Escape')
    assert result()['commits']==0
    control.focus();page.keyboard.type('Eintrag 8',delay=30)
    active=control.get_attribute('aria-activedescendant')
    assert page.locator('[id='+json.dumps(active)+']').get_attribute('data-choice-value')=='8'
    page.keyboard.press('Escape');assert result()['commits']==0
    control.click();page.keyboard.press('ArrowDown');page.keyboard.press('Enter')
    assert result()=={'value':1,'type':'number','commits':1}
    control.click();page.keyboard.press('End');page.keyboard.press('Enter')
    assert result()=={'value':99,'type':'number','commits':2}
    control.click();page.keyboard.press('Home');page.keyboard.press('Tab')
    assert result()=={'value':0,'type':'number','commits':3}
    expect(page.get_by_role('button',name='Feldsperre wechseln')).to_be_focused()
    control.click();page.get_by_role('option',name='Gesperrt',exact=True).click(force=True)
    expect(control).to_have_attribute('aria-expanded','true');assert result()['commits']==3
    page.keyboard.press('Escape')
    control.click();page.locator('fieldset').first.evaluate('(el)=>el.disabled=true')
    expect(control).to_be_disabled();expect(page.get_by_role('listbox')).to_have_count(0)
    assert result()['commits']==3
    page.get_by_role('button',name='Feldsperre wechseln').click()
    # React state is initially false while the preceding test set the native fieldset directly.
    page.get_by_role('button',name='Feldsperre wechseln').click()
    expect(control).to_be_enabled()
    for box in page.get_by_role('combobox').all():
        box.click();popup=page.get_by_role('listbox')
        expect(popup).to_be_visible()
        assert popup.get_by_role('option').count()==popup.locator('[role="option"] .choice-icon svg').count()
        assert popup.locator('svg').evaluate_all('(icons)=>icons.every(svg=>svg.querySelector("path,polygon,circle"))')
        page.keyboard.press('Escape')
    page.screenshot(path=str(args.artifacts/'svg-gallery-desktop.png'),full_page=True)
    page.get_by_role('button',name='Dialog öffnen').click()
    dialog=page.get_by_role('dialog',name='Dropdown-Prüfdialog')
    inside=dialog.get_by_role('combobox',name='Im Dialog',exact=True)
    inside.click();expect(dialog.get_by_role('listbox')).to_be_visible()
    page.keyboard.press('ArrowDown');page.keyboard.press('Escape')
    expect(dialog).to_be_visible();expect(inside).to_have_attribute('aria-expanded','false')
    assert result()['commits']==3
    choose(inside,'2');assert result()['commits']==4
    page.keyboard.press('Escape');expect(dialog).not_to_be_visible()
    for width,height,name in [(390,844,'mobile'),(820,1180,'tablet'),(1440,1000,'desktop')]:
        page.set_viewport_size({'width':width,'height':height})
        page.get_by_role('button',name='Dialog öffnen').click();inside=page.get_by_role('combobox',name='Im Dialog',exact=True)
        inside.click();popup=page.get_by_role('listbox');rect=popup.bounding_box()
        assert rect['x']>=0 and rect['x']+rect['width']<=width and rect['y']>=0 and rect['y']+rect['height']<=height+1
        page.keyboard.press('End')
        last=popup.get_by_role('option',name='Eintrag 99',exact=True)
        expect(last).to_be_in_viewport()
        page.screenshot(path=str(args.artifacts/f'dropdown-dialog-{name}.png'))
        page.keyboard.press('Escape');page.keyboard.press('Escape')
    # The real editor renders all operator menus in its native modal and canvas.
    page.set_viewport_size({'width':1440,'height':1000})
    page.goto(args.url.rstrip('/')+'/?editor=graph')
    page.get_by_role('button',name='+ Knoten',exact=True).click()
    dialog=page.get_by_role('dialog',name='Knoten hinzufügen',exact=True)
    boxes=dialog.get_by_role('combobox').all();assert len(boxes)>=9
    for box in boxes:
        box.click();popup=page.get_by_role('listbox');expect(popup).to_be_visible()
        assert popup.get_by_role('option').count()==popup.locator('.choice-icon svg').count()
        page.keyboard.press('Escape');expect(dialog).to_be_visible()
    operation=dialog.locator('[data-choice-domain="operation"]').first
    operation.click();page.screenshot(path=str(args.artifacts/'operators-in-node-picker.png'))
    page.keyboard.press('Escape');page.keyboard.press('Escape')
    # Add through the actual picker, then check menu operations do not drag or zoom nodes.
    page.get_by_role('button',name='+ Knoten',exact=True).click()
    dialog=page.get_by_role('dialog',name='Knoten hinzufügen',exact=True)
    card=dialog.locator('.catalog-card:has([data-choice-domain="operation"])').first
    card.click()
    dialog.get_by_role('button',name='Ausgewählten Knoten in Kartenmitte einfügen',exact=True).click()
    node=page.locator('.react-flow__node:has([data-choice-domain="operation"])').first
    node.locator('strong').click()
    for zoom in [0.65,1,1.5]:
        # CSS transform duplicates React Flow's viewport scaling without changing document data.
        viewport=page.locator('.react-flow__viewport')
        original=viewport.get_attribute('style')
        viewport.evaluate('(el,z)=>el.style.transform=`translate(0px,0px) scale(${z})`',zoom)
        box=node.get_by_role('combobox')
        before=node.get_attribute('style');box.click()
        popup=page.get_by_role('listbox');expect(popup).to_be_visible()
        assert popup.locator('svg').first.bounding_box()['width']==16
        page.keyboard.press('ArrowDown');page.keyboard.press('Escape')
        assert node.get_attribute('style')==before
        viewport.evaluate('(el,value)=>el.setAttribute("style",value)',original)
    box=node.get_by_role('combobox');box.click();page.screenshot(path=str(args.artifacts/'operators-on-canvas.png'))
    page.keyboard.press('Escape')
    # Committing once produces exactly one graph history transition.
    choose(box,'ADD')
    expect(box).to_contain_text('Addition')
    page.locator('.graph-heading').get_by_role('button',name='Rückgängig',exact=True).click()
    expect(node.get_by_role('combobox')).to_contain_text('Ganzzahlwert')
    page.locator('.graph-heading').get_by_role('button',name='Wiederholen',exact=True).click()
    expect(node.get_by_role('combobox')).to_contain_text('Addition')
    # A graph shortcut while a combobox is focused must not delete a node.
    count=page.locator('.react-flow__node').count();box.focus();page.keyboard.press('Delete')
    assert page.locator('.react-flow__node').count()==count
    touch=browser.new_context(viewport={'width':390,'height':844},has_touch=True,is_mobile=True)
    mobile=touch.new_page();mobile.on('pageerror',lambda error:errors.append(str(error)))
    mobile.goto(args.url.rstrip('/')+'/test/fixtures/dropdown-icons.html')
    mobile.get_by_role('combobox',name='Testauswahl',exact=True).tap()
    popup=mobile.get_by_role('listbox');rect=popup.bounding_box()
    assert popup.get_by_role('option').nth(1).bounding_box()['height']>=44
    cdp=touch.new_cdp_session(mobile)
    x=rect['x']+rect['width']/2;start=rect['y']+rect['height']-20
    cdp.send('Input.dispatchTouchEvent',{'type':'touchStart','touchPoints':[{'x':x,'y':start}]})
    for step in range(1,9):
        cdp.send('Input.dispatchTouchEvent',{'type':'touchMove','touchPoints':[{'x':x,'y':start-step*22}]})
        mobile.wait_for_timeout(30)
    cdp.send('Input.dispatchTouchEvent',{'type':'touchEnd','touchPoints':[]})
    assert popup.evaluate('(el)=>el.scrollTop')>0
    mobile.screenshot(path=str(args.artifacts/'dropdown-touch.png'))
    touch.close()
    assert not errors,errors
    browser.close()
print('SVG dropdown checks passed: icons, typed values, disabled states, keyboard commits, Escape, modal layering, responsive viewports and graph zoom/drag/shortcut isolation.')
