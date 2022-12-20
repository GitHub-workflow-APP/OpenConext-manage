import React from "react";
import I18n from "i18n-js";
import PropTypes from "prop-types";
import {Link} from "react-router-dom";
import SelectEntities from "./../SelectEntities";
import {Select} from "./../../components";
import {stop} from "../../utils/Utils";

import "./ConsentDisabling.scss";

export default class ConsentDisabling extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            sorted: "name",
            reverse: false,
            enrichedDisableConsent: []
        };
    }

    componentDidMount() {
        this.initialiseDisableConsent(this.props.whiteListing);
    }

    componentWillReceiveProps(nextProps) {
        if (nextProps.allowedall !== this.props.allowedall ||
            (nextProps.whiteListing || []).length !== (this.props.whiteListing || []).length ||
            (nextProps.allowedEntities || []).length !== (this.props.allowedEntities || []).length) {
            this.initialiseDisableConsent(nextProps.whiteListing);
        }
    }

    initialiseDisableConsent(whiteListing) {
        window.scrollTo(0, 0);
        const {disableConsent} = this.props;

        const enrichedDisableConsent = disableConsent
            .map(entity => this.enrichSingleDisableConsent(entity, whiteListing))
            .filter(enriched => enriched !== null);

        this.setDisableConsentState(enrichedDisableConsent);
    }

    enrichSingleDisableConsent = (disableConsent, whiteListing) => {
        const moreInfo = whiteListing.find(
            entry => entry.data.entityid === disableConsent.name
        );
        if (moreInfo === undefined) {
            //this can happen as SP's are deleted
            return null;
        }
        return {
            status: I18n.t(`metadata.${moreInfo.data.state}`),
            entityid: disableConsent.name,
            name:
                moreInfo.data.metaDataFields["name:en"] ||
                moreInfo.data.metaDataFields["name:nl"] ||
                "",
            organization:
                moreInfo.data.metaDataFields["OrganizationName:en"] ||
                moreInfo.data.metaDataFields["OrganizationName:nl"] ||
                "",
            id: moreInfo["_id"],
            type: disableConsent.type,
            entityType: moreInfo.type,
            "explanation:en": disableConsent["explanation:en"],
            "explanation:nl": disableConsent["explanation:nl"]
        };
    };

    setDisableConsentState = newDisableConsent => {
        this.setState({
            enrichedDisableConsent: newDisableConsent.sort(
                this.sortByAttribute(this.state.sorted, this.state.reverse)
            )
        });
    };

    addDisableConsent = entityid => {
        const entity = {
            name: entityid,
            type: "no_consent",
            "explanation:nl": "",
            "explanation:en": ""
        };
        const {disableConsent, whiteListing} = this.props;
        const newState = [...disableConsent].concat(entity);
        this.props.onChange("data.disableConsent", newState);

        const newDisableConsent = [...this.state.enrichedDisableConsent].concat(
            this.enrichSingleDisableConsent(entity, whiteListing)
        );

        this.setDisableConsentState(newDisableConsent);
    };

    removeDisableConsent = entry => {
        const {disableConsent} = this.props;
        const newState = [...disableConsent].filter(
            entity => entity.name !== entry.entityid
        );
        this.props.onChange("data.disableConsent", newState);

        const newDisableConsent = [...this.state.enrichedDisableConsent].filter(
            entity => entity.entityid !== entry.entityid
        );
        this.setDisableConsentState(newDisableConsent);
    };

    onChangeSelectConsentValue = (entry, type) => {
        entry.type = type;
        const newState = [...this.props.disableConsent];
        const pos = newState.map(e => e.name).indexOf(entry.entityid);
        newState[pos] = {
            name: entry.entityid,
            type: type,
            "explanation:nl": entry["explanation:nl"],
            "explanation:en": entry["explanation:en"]
        };
        this.props.onChange("data.disableConsent", newState);
    };

    onChangeExplanation = (entry, explanation, language) => {
        entry[`explanation:${language}`] = explanation;
        const newState = [...this.props.disableConsent];
        const pos = newState.map(e => e.name).indexOf(entry.entityid);
        const explanationNl =
            language === "nl" ? explanation : entry["explanation:nl"];
        const explanationEn =
            language === "en" ? explanation : entry["explanation:en"];
        newState[pos] = {
            name: entry.entityid,
            type: entry.type,
            "explanation:nl": explanationNl,
            "explanation:en": explanationEn
        };
        this.props.onChange("data.disableConsent", newState);
    };

    sortByAttribute = (name, reverse = false) => (a, b) => {
        const aSafe = a[name] || "";
        const bSafe = b[name] || "";
        return (
            aSafe.toString().localeCompare(bSafe.toString()) * (reverse ? -1 : 1)
        );
    };

    sortTable = (enrichedDisableConsent, name) => () => {
        const reverse = this.state.sorted === name ? !this.state.reverse : false;
        const sorted = [...enrichedDisableConsent].sort(
            this.sortByAttribute(name, reverse)
        );
        this.setState({
            enrichedDisableConsent: sorted,
            sorted: name,
            reverse: reverse
        });
    };

    renderDisableConsent = (entity, guest) => {
        return (
            <tr key={entity.entityid}>
                <td className="remove">
                    {!guest && (
                        <span>
              <a
                  onClick={e => {
                      stop(e);
                      this.removeDisableConsent(entity);
                  }}
              >
                <i className="fa fa-trash-o"/>
              </a>
            </span>
                    )}
                </td>
                <td>{entity.status}</td>
                <td>{entity.name}</td>
                <td>{entity.organization}</td>
                <td>
                    <Select
                        name="select-consent-value"
                        className="select-consent-value"
                        onChange={option =>
                            this.onChangeSelectConsentValue(entity, option.value)
                        }
                        options={[
                            {
                                label: I18n.t("consentDisabling.entries.no_consent"),
                                value: "no_consent"
                            },
                            {
                                label: I18n.t("consentDisabling.entries.minimal_consent"),
                                value: "minimal_consent"
                            },
                            {
                                label: I18n.t("consentDisabling.entries.default_consent"),
                                value: "default_consent"
                            }
                        ]}
                        value={entity.type}
                        isSearchable={false}
                    />
                </td>
                <td className="explanation">
                    <input
                        type="text"
                        value={entity["explanation:nl"]}
                        onChange={e =>
                            this.onChangeExplanation(entity, e.target.value, "nl")
                        }
                    />
                </td>
                <td className="explanation">
                    <input
                        type="text"
                        value={entity["explanation:en"]}
                        onChange={e =>
                            this.onChangeExplanation(entity, e.target.value, "en")
                        }
                    />
                </td>
                <td>
                    <Link to={`/metadata/${entity.entityType}/${entity.id}`} target="_blank">
                        {entity.entityid}
                    </Link>
                </td>
            </tr>
        );
    };

    renderDisableConsentTable = (enrichedDisableConsent, guest) => {
        const {sorted, reverse} = this.state;
        const icon = name => {
            if (!(name === sorted)) {
                return <i className="fa fa-arrow-down"/>;
            }

            if (reverse) {
                return <i className="fa fa-arrow-up reverse"/>;
            }

            return <i className="fa fa-arrow-down current"/>;
        };
        const th = name => (
            <th
                key={name}
                className={name}
                onClick={this.sortTable(enrichedDisableConsent, name)}
            >
                {I18n.t(`consentDisabling.entries.${name}`)}
                {icon(name)}
            </th>
        );
        const names = [
            "status",
            "name",
            "organization",
            "consent_value",
            "explanationNl",
            "explanationEn",
            "entityid"
        ];
        return (
            <section className="consent-disabling">
                <table>
                    <thead>
                    <tr>
                        <th className="remove"/>
                        {names.map(th)}
                    </tr>
                    </thead>
                    <tbody>
                    {enrichedDisableConsent.map(entity =>
                        this.renderDisableConsent(entity, guest)
                    )}
                    </tbody>
                </table>
            </section>
        );
    };

    filterEntityOptions(allowedAll, allowedEntities, whiteListing) {
        if (allowedAll) {
            return whiteListing;
        }
        const allowedEntityNames = allowedEntities.map(ent => ent.name);

        return whiteListing.filter(entry =>
            allowedEntityNames.includes(entry.data.entityid)
        );
    }

    render() {
        const {disableConsent, name, guest, allowedAll, allowedEntities, whiteListing} = this.props;
        const placeholder = I18n.t("consentDisabling.placeholder");
        const {enrichedDisableConsent} = this.state;

        return (
            <div className="metadata-consent-disabling">
                <div className="consent-disabling-info">
                    <h2>{I18n.t("consentDisabling.title")}</h2>
                    {!guest && (
                        <p>{I18n.t("consentDisabling.description", {name: name})}</p>
                    )}
                </div>
                {!guest && (
                    <SelectEntities
                        whiteListing={this.filterEntityOptions(allowedAll, allowedEntities, whiteListing)}
                        allowedEntities={disableConsent}
                        onChange={this.addDisableConsent}
                        placeholder={placeholder}
                    />
                )}
                {enrichedDisableConsent.length > 0 &&
                this.renderDisableConsentTable(
                    enrichedDisableConsent,
                    guest
                )}
            </div>
        );
    }
}

ConsentDisabling.propTypes = {
    disableConsent: PropTypes.array.isRequired,
    allowedEntities: PropTypes.array.isRequired,
    name: PropTypes.string.isRequired,
    whiteListing: PropTypes.array.isRequired,
    onChange: PropTypes.func.isRequired,
    guest: PropTypes.bool.isRequired,
    allowedAll: PropTypes.bool.isRequired
};
