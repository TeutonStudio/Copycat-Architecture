import { BlockIcon } from './BlockIcon.jsx'
import { Modal } from './Modal.jsx'
function Item({id,count}) {
  const name=id.split(':').at(-1).replaceAll('_',' ')
  return <li><BlockIcon id={id}/><span>{name}<br/><code>{id}</code></span><strong>{count}</strong></li>
}
export function MaterialDialog({plan,target,onClose}) {
  return <Modal title={`Materialbedarf · ${target}`} onClose={onClose}><p>Bedarf des nativen Planauswerters · Registry-IDs zusammengefasst</p><ul className="material-requirements">{Object.entries(plan.materials || {}).sort(([a],[b]) => a.localeCompare(b)).map(([id,count]) => <Item key={id} id={id} count={count} />)}</ul>{!Object.keys(plan.materials || {}).length && <p>Keine Oberflächenmaterialien.</p>}<p><strong>Zink: {plan.raw.toLocaleString('de-DE')} Einheiten</strong></p><footer><button onClick={onClose}>Schließen</button></footer></Modal>
}
