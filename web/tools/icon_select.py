"""Operate Graphstudio's shared accessible select by stable value or visible label."""
import json

def choose(control, value=None, label=None):
    control.click()
    popup=control.page.locator('[id='+json.dumps(control.get_attribute('aria-controls'))+']')
    option=popup.get_by_role('option',name=label,exact=True) if label is not None else popup.locator('[data-choice-value='+json.dumps(str(value))+']')
    option.click()

def option_labels(control):
    control.click()
    popup=control.page.locator('[id='+json.dumps(control.get_attribute('aria-controls'))+']')
    labels=popup.locator('.icon-select-label').all_text_contents()
    control.press('Escape')
    return labels
