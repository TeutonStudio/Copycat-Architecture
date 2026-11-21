import { Component } from 'react'
import { Modal } from './Modal.jsx'
// A failed selection or renderer must never unmount the graph and its draft.
export class SelectionBoundary extends Component {
  state = {error:null}
  static getDerivedStateFromError(error) { return {error} }
  render() {
    if(!this.state.error) return this.props.children
    return <Modal title="Formauswahl konnte nicht geöffnet werden" onClose={this.props.onClose}>
      <p role="alert">{this.state.error.message || 'Die Formauswahl ist fehlgeschlagen.'}</p>
      <p>Schließe die Auswahl oder versuche es erneut. Dein Graph bleibt erhalten.</p>
      <footer><button onClick={this.props.onClose}>Zurück zum Graphen</button><button onClick={()=>this.setState({error:null})}>Erneut versuchen</button></footer>
    </Modal>
  }
}
