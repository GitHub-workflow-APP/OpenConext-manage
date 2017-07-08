import React from "react";
import PropTypes from "prop-types";
import Select from "react-select";
import "react-select/dist/react-select.css";
import "./SelectNewMetaDataField.css";

const patternPropertyRegex = /\^(.*)(\(.*?\))(.*)\$/g;
const enumPropertyRegex = /\^(.*)(\(en\|nl\))(.*)\$/g;
const multiplicityRegex = /.*:(\d)[:]{0,1}.*/;

export default class SelectNewMetaDataField extends React.PureComponent {

    options = (configuration, metaDataFields) => {
        /*
         * First get all the keys from the configuration for which there is no metaDataField
         */
        const metaDataKeys = Object.keys(metaDataFields);
        const properties = Object.keys(configuration.properties.metaDataFields.properties);
        const missingProperties = properties.filter(property => metaDataKeys.indexOf(property) === -1);

        /*
         * Do the same for the patternProperties
         */
        const patternProperties = configuration.properties.metaDataFields.patternProperties;
        const patternPropertiesKeys = Object.keys(configuration.properties.metaDataFields.patternProperties);
        const missingPatternProperties = patternPropertiesKeys
            .reduce((accumulator, patternPropertyKey) => {
                let patternProperty = patternProperties[patternPropertyKey];
                const ref = patternProperty["$ref"];
                if (ref) {
                    //"#/definitions/AssertionConsumerServiceBinding"
                    const refDefinition = configuration.definitions[ref.substring(ref.lastIndexOf("/") + 1)];
                    patternProperty = {...refDefinition, ...patternProperty};
                }
                return this.addMissingProperty(accumulator, patternPropertyKey, patternProperty, metaDataKeys);
            }, []);
        return missingProperties.concat(missingPatternProperties).map(prop => {
            return {value: prop, label: prop};
        }).sort((a, b) => a.value.toLowerCase().localeCompare(b.value.toLowerCase()));
    };

    addMissingProperty = (accumulator, patternPropertyKey, patternProperty, metaDataKeys) => {
        patternPropertyRegex.lastIndex = 0;
        enumPropertyRegex.lastIndex = 0;
        multiplicityRegex.lastIndex = 0;

        const regExp = new RegExp(patternPropertyKey);
        const existingMetaDataKeys = metaDataKeys.filter(metaDataKey => regExp.test(metaDataKey));
        let enumExec;
        if (existingMetaDataKeys.length === 0) {
            //translate the patternPropertyKey to a metaDataKey and add it to the accumulator
            if (patternProperty.multiplicity) {
                const newMetaDataKey = patternPropertyKey.replace(patternPropertyRegex, `$1${patternProperty.startIndex || 0}$3`);
                accumulator.push(newMetaDataKey);
            } else if (enumExec = enumPropertyRegex.exec(patternPropertyKey)) {
                accumulator.concat([`${enumExec[1]}:nl`, `${enumExec[1]}:en`]);
            } else {
                throw new Error("Not supported patternProperty " + patternPropertyKey);
            }
        } else {
            //find the highest cardinal
            if (patternProperty.multiplicity) {
                const highestMetaDataKey = existingMetaDataKeys.sort()[existingMetaDataKeys.length - 1];
                const multiplicityParsed = multiplicityRegex.exec(highestMetaDataKey);
                const highest = parseInt(multiplicityParsed[1]);
                if (highest < (patternProperty.multiplicity + (patternProperty.startIndex || -1))) {
                    const newMetaDataKey = patternPropertyKey.replace(patternPropertyRegex, `$1${highest + 1}$3`);
                    accumulator.push(newMetaDataKey);

                }
            } else if (enumExec = enumPropertyRegex.exec(patternPropertyKey)) {
                if (existingMetaDataKeys.length < 2) {
                    const missingLang = existingMetaDataKeys[0].indexOf(":en") > 0 ? "nl" : "en";
                    accumulator.push(`${enumExec[1]}:${missingLang}`);
                }
            } else {
                throw new Error("Not supported patternProperty " + patternPropertyKey);
            }
        }
        return accumulator;
    };

    render() {
        const {onChange, configuration, metaDataFields, placeholder} = this.props;
        return <Select className="select-new-metadata"
                       onChange={option => onChange(option.value)}
                       options={this.options(configuration, metaDataFields)}
                       value={null}
                       searchable={true}
                       placeholder={placeholder || "Select..."}/>;
    }

}

SelectNewMetaDataField.propTypes = {
    onChange: PropTypes.func.isRequired,
    configuration: PropTypes.object.isRequired,
    metaDataFields: PropTypes.object.isRequired,
    placeholder: PropTypes.string
};


