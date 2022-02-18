import React from "react";
import PropTypes from "prop-types";
import I18n from "i18n-js";
import "./Password.scss";
import {secret} from "../../api";
import CopyToClipboard from "react-copy-to-clipboard";
import ReactTooltip from "react-tooltip";

export default class Password extends React.PureComponent {

  state = {
    value: this.props.value,
    disabled: true,
    showSaveWarning: false,
    showLengthWarning: false,
    copied: false
  };

  componentDidMount() {
    if (!this.props.value) {
      this.handleGenerate();
    }
  }

  handleEdit() {
    this.setState(
      {
        disabled: false,
        value: "",
      },
      () => this.passwordInput.focus()
    );
  }

  handleCopy = () => this.setState({"copied": true},
    () => setTimeout(() => this.setState({"copied": false}), 1500));

  handleUndo() {
    this.setState({
      value: this.props.value,
      disabled: true,
      showSaveWarning: false,
      showLengthWarning: false
    }, () => this.props.hasError(this.props.name, false));
  }

  handleGenerate() {
    secret().then(json => this.setState({
      value: json.secret,
      disabled: true,
      showSaveWarning: false,
      showLengthWarning: false
    }, () => {
      this.props.hasError(this.props.name, false);
      this.props.onChange(this.state.value);
    }));

  }

  handleSave() {
    const {value} = this.state;
    const {minLength} = this.props;

    if (value && minLength && value.length < minLength) {
      this.setState({showLengthWarning: true});
      this.props.hasError(this.props.name, true);
    } else {
      this.props.onChange(value);
      this.props.hasError(this.props.name, false);

      this.setState({
        disabled: true,
        showSaveWarning: false,
        showLengthWarning: false
      });
    }

  }

  renderIcon = (id, className, tooltipKey) =>
    <span>
          <i className={className} data-for={`${this.props.name}-${id}`} data-tip/>
          <ReactTooltip
            id={`${this.props.name}-${id}`}
            type="info"
            class="tool-tip"
            effect="solid">
            <span>{I18n.t(`password.${tooltipKey}`)}</span>
          </ReactTooltip>
      </span>;


  renderDisabledIcon(copied) {
    const classNameCopy = copied ? "copy copied" : "copy";
    return (
      <div className="password-icon-container">
        <div className="password-icon">
          <CopyToClipboard text={this.state.value} onCopy={this.handleCopy}>
            {this.renderIcon("copy-icon", `fa fa-copy ${classNameCopy}`, "copy")}
          </CopyToClipboard>
        </div>

        <span className="separator"/>

        <div className="password-icon" onClick={() => this.handleEdit()}>
          {this.renderIcon("edit-icon", "fa fa-pencil edit", "edit")}
        </div>
      </div>
    );
  }

  renderEnabledIcons() {
    return (
      <div className="password-icon-container">
        <div className="password-icon" onClick={() => this.handleGenerate()}>
          {this.renderIcon("key-icon", "fa fa-key key", "key")}
        </div>

        <span className="separator"/>

        <div className="password-icon" onClick={() => this.handleUndo()}>
          {this.renderIcon("undo-icon", "fa fa-undo undo", "undo")}
        </div>

        <span className="separator"/>

        <div className="password-icon" onClick={() => this.handleSave()}>
          {this.renderIcon("save-icon", "fa fa-save save", "save")}
        </div>
      </div>
    );
  }

  render() {
    const {value, showSaveWarning, showLengthWarning, copied} = this.state;
    const {hasFormatError, onChange, minLength, hasError, ...rest} = this.props;

    const disabled = this.state.disabled || this.props.disabled;

    return (
      <div>
        <div
          className={"password-field " + (disabled ? "disabled" : "")}
          onBlur={() => this.setState({showSaveWarning: true})}
          onFocus={() => this.setState({showSaveWarning: true})}
        >
          <input
            {...rest}
            {...{value, disabled}}
            type="text"
            className="password-input"
            onChange={e => this.setState({value: e.target.value})}
            ref={el => {
              this.passwordInput = el;
            }}
          />

          <span className="separator"/>
          {disabled ? this.renderDisabledIcon(copied) : this.renderEnabledIcons()}
        </div>

        {showSaveWarning && (
          <span className="error">
            <i className="fa fa-warning"/>
            Save the new submission inline for it to be saved on submit
          </span>
        )}
        {showLengthWarning && (
          <span className="error">
            <i className="fa fa-warning"/>
            Minimal length for this password / secret is {minLength}
          </span>
        )}

      </div>
    );
  }
}

Password.propTypes = {
  name: PropTypes.string.isRequired,
  value: PropTypes.string.isRequired,
  format: PropTypes.string.isRequired,
  onChange: PropTypes.func.isRequired,
  hasError: PropTypes.func.isRequired,
  minLength: PropTypes.number,
  autoFocus: PropTypes.bool,
  isRequired: PropTypes.bool,
  disabled: PropTypes.bool
};
