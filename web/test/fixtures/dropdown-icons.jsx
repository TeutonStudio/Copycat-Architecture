import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { IconSelect } from '../../src/IconSelect.jsx'
import { ChoiceIcon } from '../../src/ChoiceIcon.jsx'
import { fixedChoices, iconPaths, operationIcons, choiceIcon } from '../../src/choice-icons.js'
import { Modal } from '../../src/Modal.jsx'
import '../../src/style.css'
function Fixture() {
  const [value,setValue]=useState(0),[commits,setCommits]=useState(0),[modal,setModal]=useState(false),[disabled,setDisabled]=useState(false)
  const change=value=>{setValue(value);setCommits(n=>n+1)}
  return <main style={{display:'block',overflow:'visible',padding:24,maxWidth:1100,margin:'auto'}}>
    <h1>SVG-Auswahlkatalog</h1><output id="result">{JSON.stringify({value,type:typeof value,commits})}</output>
    <fieldset disabled={disabled}><label>Testauswahl<IconSelect domain="input" aria-label="Testauswahl" value={value} onValueChange={change} options={[{value:-1,label:'Gesperrt',disabled:true},...Array.from({length:100},(_,value)=>({value,label:`Eintrag ${value}`}))]}/></label></fieldset>
    <button onClick={()=>setDisabled(value=>!value)}>Feldsperre wechseln</button><button onClick={()=>setModal(true)}>Dialog öffnen</button><button>Nach der Auswahl</button>
    {modal && <Modal title="Dropdown-Prüfdialog" onClose={()=>setModal(false)}><label>Im Dialog<IconSelect domain="input" aria-label="Im Dialog" value={value} onValueChange={change} options={Array.from({length:100},(_,value)=>({value,label:`Eintrag ${value}`}))}/></label></Modal>}
    <section id="gallery" style={{display:'grid',gridTemplateColumns:'repeat(auto-fit,minmax(135px,1fr))',gap:12,marginTop:32}}>
    {Object.keys(iconPaths).map(kind=><div key={kind} style={{display:'flex',alignItems:'center',gap:8}}><ChoiceIcon icon={{kind}}/><small>{kind}</small></div>)}
    {Object.keys(operationIcons).map(value=><div key={value} style={{display:'flex',alignItems:'center',gap:8}}><ChoiceIcon icon={choiceIcon('operation',value)}/><small>{value}</small></div>)}
    </section>
    <section style={{display:'grid',gridTemplateColumns:'repeat(auto-fit,minmax(180px,1fr))',gap:12,marginTop:32}}>
    {Object.entries(fixedChoices).map(([domain,values])=><label key={domain}>{domain}<IconSelect domain={domain} aria-label={domain} value={Object.keys(values)[0]} options={Object.keys(values).map(value=>({value,label:value}))}/></label>)}
    </section>
  </main>
}
createRoot(document.getElementById('root')).render(<Fixture/> )
