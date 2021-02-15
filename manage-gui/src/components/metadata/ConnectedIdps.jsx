import React from "react";
import I18n from "i18n-js";
import PropTypes from "prop-types";
import {Link} from "react-router-dom";

import "./ConnectedIdps.css";
import {copyToClip, isEmpty} from "../../utils/Utils";
import NotesTooltip from "../NotesTooltip";
import {getConnectedEntities} from "../../utils/TabNumbers";

export default class ConnectedIdps extends React.Component {

  constructor(props) {
    super(props);
    this.state = {
      providerType: "Identity Providers",
      sorted: "name",
      reverse: false,
      connectedEntities: [],
      filteredConnectedEntities: [],
      query: "",
      copiedToClipboardClassName: ""
    };
  }

  componentDidMount() {
    this.initialiseConnectedIdps(this.props.whiteListing);
  }

  initialiseConnectedIdps(whiteListing) {
    window.scrollTo(0, 0);
    const {allowedAll, allowedEntities = [], entityId, state} = this.props;
    const entities =  getConnectedEntities(whiteListing, allowedAll , allowedEntities, entityId, state)
      .map(idp => ({
        id: idp._id,
        name: idp.data.metaDataFields["name:en"] || idp.data.metaDataFields["name:nl"] || idp.data.entityid,
        status: idp.data.state,
        entityid: idp.data.entityid,
        notes: idp.data.notes
      }));
    const sorted = entities.sort(this.sortByAttribute("name", false));
    this.setState({connectedEntities: entities, filteredConnectedEntities: sorted});
  }

  componentWillReceiveProps(nextProps) {
    if (nextProps.whiteListing && this.props.whiteListing &&
      nextProps.whiteListing.length !== this.props.whiteListing.length) {
      this.initialiseConnectedIdps(nextProps.whiteListing);
    }
  }

  sortByAttribute = (name, reverse = false) => (a, b) => {
    const aSafe = a[name] || "";
    const bSafe = b[name] || "";
    return aSafe.toString().localeCompare(bSafe.toString()) * (reverse ? -1 : 1);
  };

  sortTable = (filteredConnectedEntities, name, reversed) => () => {
    const reverse = reversed || (this.state.sorted === name ? !this.state.reverse : false);
    const sorted = [...filteredConnectedEntities].sort(this.sortByAttribute(name, reverse));
    this.setState({filteredConnectedEntities: sorted, sorted: name, reverse: reverse});
  };

  search = e => {
    const query = e.target.value ? e.target.value.toLowerCase() : "";
    const {sorted, reverse, connectedEntities} = this.state;
    const names = ["name", "status", "entityid"];
    const result = isEmpty(query) ? connectedEntities : connectedEntities.filter(idp => names.some(name =>
      idp[name].toLowerCase().indexOf(query) > -1));
    this.setState({query: query, filteredConnectedEntities: result.sort(this.sortByAttribute(sorted, reverse))});
  };

  copyToClipboard = () => {
    copyToClip("entities-printable");
    this.setState({copiedToClipboardClassName: "copied"});
    setTimeout(() => this.setState({copiedToClipboardClassName: ""}), 5000);
  };

  renderIdP = (entity, type) => {
    return <tr key={entity.id}>
      <td>
        <Link to={`/metadata/${type}/${entity.id}`} target="_blank">
          {entity.name}
        </Link>
      </td>
      <td>
        {entity.status}
      </td>
      <td>
        {entity.entityid}
      </td>
      <td className="info">
        {isEmpty(entity.notes) ? <span></span> :
          <NotesTooltip identifier={entity.entityid} notes={entity.notes}/>}
      </td>
    </tr>
  };

  renderConnectedIdpTablePrintable = entries =>
    <section id="entities-printable"
             className="entities-printable">{entries.map(entity => `${entity.name ? entity.name + '\t' : ''}${entity.entityid}`).join("\n")}</section>;

  renderConnectedIdpTable = (entries) => {
    const {sorted, reverse} = this.state;
    const icon = name => {
      return name === sorted ? (reverse ? <i className="fa fa-arrow-up reverse"></i> :
        <i className="fa fa-arrow-down current"></i>)
        : <i className="fa fa-arrow-down"></i>;
    };
    const th = name =>
      <th key={name} className={name}
          onClick={this.sortTable(entries, name)}>{I18n.t(`whitelisting.allowedEntries.${name}`)}{icon(name)}</th>
    const names = ["name", "status", "entityid", "notes"];
    return <section className="entities">
      <table>
        <thead>
        <tr>
          {names.map(th)}
        </tr>
        </thead>
        <tbody>
        {entries.map(entity => this.renderIdP(entity, "saml20_idp"))}
        </tbody>
      </table>

    </section>
  };

  render() {
    const {providerType, filteredConnectedEntities, query, connectedEntities, copiedToClipboardClassName} = this.state;
    const {name} = this.props;
    return (
      <div className="metadata-connected-idps">
        {connectedEntities.length > 0 && <section className="search">
          <div className="search-input-container">
            <input className="search-input"
                   placeholder={I18n.t("connectedIdps.searchPlaceHolder")}
                   type="text"
                   onChange={this.search}
                   value={query}/>
            <i className="fa fa-search"></i>
          </div>
          <span className={`button green ${copiedToClipboardClassName}`} onClick={this.copyToClipboard}>
                            {I18n.t("clipboard.copy")}<i className="fa fa-clone"></i>
                        </span>
        </section>}
        {connectedEntities.length > 0 && this.renderConnectedIdpTable(filteredConnectedEntities)}
        {this.renderConnectedIdpTablePrintable(filteredConnectedEntities)}
        {connectedEntities.length === 0 &&
        <h3>{I18n.t("connectedIdps.noConnections", {type: providerType, name: name})}</h3>}
      </div>
    );
  }
}

ConnectedIdps.propTypes = {
  whiteListing: PropTypes.array.isRequired,
  allowedEntities: PropTypes.array.isRequired,
  allowedAll: PropTypes.bool.isRequired,
  entityId: PropTypes.string.isRequired,
  name: PropTypes.string.isRequired,
  state: PropTypes.string.isRequired
};

