/** Export to Terra FAB */

import React from "react";
import { withStyles } from "@material-ui/core/styles";
import Button from "@material-ui/core/Button";
import Tooltip from "@material-ui/core/Tooltip";
import TextField from "@material-ui/core/TextField";
import Dialog from "@material-ui/core/Dialog";
import DialogActions from "@material-ui/core/DialogActions";
import DialogContent from "@material-ui/core/DialogContent";

import Send from "@material-ui/icons/Send";
import zIndex from "@material-ui/core/styles/zIndex";

const styles = {
  uploadIcon: {
    marginLeft: "10px"
  },
  cohort: {
    textAlign: "center",
    margin: "15px"
  },

  cohortInput: {
    width: "100%",
    outlineWidth: "0",
    border: "0",
    borderBottom: "3px solid silver",
    outline: "none",
    fontSize: "16px",
    margin: "0px 40px 0px 0px",
    "&:focus": {
      borderBottom: "3px solid blue",
      color: "Silver",
      "&::placeholder": {
        color: "blue",
        top: "-20px",
        position: "relative"
      }
    },
    "&::placeholder": {
      fontSize: "16px"
    }
  },
  cohortWarning: {
    verticalAlign: "top",
    fontSize: "12px",
    color: "silver"
  },
  cohortButton: {
    fontSize: "14px",
    border: "0",
    padding: "7px",
    borderRadius: "4px",
    color: "blue",
    "&:hover": {
      backgroundColor: "silver"
    }
  }
};

class ExportFab extends React.Component {
  constructor(props) {
    super(props);
    this.state = { open: false };
    this.handleClick = this.handleClick.bind(this);
    this.handleSave = this.handleSave.bind(this);
    this.handleCancel = this.handleCancel.bind(this);
    this.setTextValue = this.setTextValue.bind(this);
  }

  render() {
    const { classes, counts } = this.props;
    const totalCount =
      counts === null ? NaN : counts.donor_count + counts.file_count;
    const allowExport =
      !isNaN(totalCount) && totalCount > 0 && totalCount < 10000;

    let tooltipText;
    if (allowExport) {
      tooltipText = "Send to Terra";
    } else if (totalCount === 0) {
      tooltipText = "Nothing to export! Please remove filters";
    } else {
      tooltipText = "Too many records to export! Please apply more filters";
    }

    let button;
    if (allowExport) {
      button = (
        <Button
          variant="contained"
          color="secondary"
          onClick={this.handleClick}
        >
          Export <Send className={classes.uploadIcon} />
        </Button>
      );
    } else {
      button = (
        <Button variant="contained" color="secondary" disabled>
          Export <Send className={classes.uploadIcon} />
        </Button>
      );
    }

    return (
      <div>
        <div className={classes.exportFab}>
          <Tooltip title={tooltipText}>
            <div>{button}</div>
          </Tooltip>
        </div>
        <div>
          <Dialog
            open={this.state.open}
            onClose={this.handleClose}
            aria-labelledby="form-dialog-title"
          >
            <div className={classes.cohort}>
              <form onSubmit={() => this.handleSave()}>
                <DialogContent>
                  <input
                    className={classes.cohortInput}
                    id="name"
                    label="Cohort Name"
                    type="text"
                    pattern="[A-Za-z-_0-9]+"
                    required="required"
                    onChange={this.setTextValue}
                    placeholder="Cohort Name"
                    title="alphanumeric characters, '_', and/or '-'"
                  />
                  <div className={classes.cohortWarning}>
                    A cohort with this name will be created in Terra.
                  </div>
                </DialogContent>
                <DialogActions>
                  <button
                    className={classes.cohortButton}
                    type="button"
                    onClick={() => this.handleCancel()}
                  >
                    CANCEL
                  </button>
                  <button
                    className={classes.cohortButton}
                    id="save"
                    type="submit"
                  >
                    SEND
                  </button>
                </DialogActions>
              </form>
            </div>
            {/*<DialogContent>
              <TextField
                autoFocus
                onChange={this.setTextValue}
                margin="dense"
                id="name"
                label="Cohort Name"
                helperText="A cohort with this name will be created in Terra"
                type="text"
                fullWidth
                onKeyPress={ev => {
                  if (ev.key === "Enter") {
                    this.handleSave();
                  }
                }}
              />
            </DialogContent>
            <DialogActions>
              <Button onClick={this.handleCancel} color="primary">
                Cancel
              </Button>
              <Button id="save" onClick={this.handleSave} color="primary">
                Send
              </Button>
            </DialogActions>*/}
          </Dialog>
        </div>
      </div>
    );
  }

  setTextValue(event) {
    this.setState({ cohortName: event.target.value });
  }

  handleClick() {
    var filter = this.props.filter;
    if (filter != null && filter.length > 0) {
      this.setState(state => ({ open: true }));
    } else {
      this.handleSave();
    }
  }

  handleCancel() {
    this.setState(state => ({ open: false }));
  }

  handleSave() {
    this.setState(state => ({ open: false }));

    const cohortName = this.state.cohortName;
    const filter = this.props.filter;

    let exportUrl = this.props.apiBasePath + "/export";
    const exportParams = [];

    if (filter.length > 0) {
      exportParams.push("filter=" + filter.join("|"));
    }
    if (cohortName) {
      exportParams.push("cohortName=" + cohortName);
    }

    if (exportParams.length > 0) {
      exportUrl += "?" + exportParams.join("&");
    }

    // We have to encode the URI twice(!!!) because Terra's import page will
    // decode once before fetching, and we need any invalid characters to stay encoded.
    const importBase =
      "https://app.terra.bio/#import-data?format=entitiesJson&url=";
    const encodedExport = encodeURI(exportUrl);
    window.open(importBase + encodeURIComponent(encodedExport));
  }
}

export default withStyles(styles)(ExportFab);
