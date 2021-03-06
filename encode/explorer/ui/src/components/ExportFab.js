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

const styles = {
  exportButton: {
    backgroundColor: "#fff"
  },
  exportButtonText: {
    color: "rgb(90, 166, 218)"
  },
  uploadIcon: {
    marginLeft: "10px",
    color: "rgb(90, 166, 218)"
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
      borderBottom: "3px solid rgb(90, 166, 218)",
      color: "rgb(90, 166, 218)",
      "&::placeholder": {
        color: "rgb(90, 166, 218)",
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
    color: "rgb(90, 166, 218)",
    "&:hover": {
      backgroundColor: "rgb(90, 166, 218)",
      color: "white"
    }
  }
};

class ExportFab extends React.Component {
  constructor(props) {
    super(props);

    this.cohortPattern = /^[a-zA-Z0-9\-_]*$/;
    this.infoText = "A cohort with this name will be created in Terra";
    this.errText = "Only letters, numbers, '_' and '-' are allowed";

    this.state = {
      open: false,
      cohortName: "",
      showError: false
    };

    this.handleClick = this.handleClick.bind(this);
    this.handleSave = this.handleSave.bind(this);
    this.handleCancel = this.handleCancel.bind(this);
    this.setTextValue = this.setTextValue.bind(this);
  }

  render() {
    const { classes, counts } = this.props;
    const { cohortName, open, showError } = this.state;

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
          className={classes.exportButton}
          variant="contained"
          onClick={this.handleClick}
        >
          <div className={classes.exportButtonText}>Export</div>
          <Send className={classes.uploadIcon} />
        </Button>
      );
    } else {
      button = (
        <Button className={classes.exportButton} disabled variant="contained">
          <div className={classes.exportButtonText}>Export</div>
          <Send className={classes.uploadIcon} />
        </Button>
      );
    }

    return (
      <div>
        <div>
          <Tooltip title={tooltipText}>
            <div>{button}</div>
          </Tooltip>
        </div>
        <div>
          <Dialog
            open={open}
            onClose={this.handleClose}
            aria-labelledby="form-dialog-title"
          >
            <DialogContent>
              <TextField
                autoFocus
                error={showError}
                onChange={this.setTextValue}
                margin="dense"
                id="name"
                label="Cohort Name"
                helperText={showError ? this.errText : this.infoText}
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
              <Button
                id="save"
                onClick={this.handleSave}
                color="primary"
                disabled={showError || cohortName.length == 0}
              >
                Send
              </Button>
            </DialogActions>
          </Dialog>
        </div>
      </div>
    );
  }

  setTextValue(event) {
    const cohortName = event.target.value;
    this.setState({
      cohortName,
      showError: !this.cohortPattern.test(cohortName)
    });
  }

  handleClick() {
    this.setState({ open: true });
  }

  handleCancel() {
    this.setState({ open: false });
  }

  handleSave() {
    const { apiBasePath, filter } = this.props;
    const { cohortName } = this.state;

    this.setState({ open: false });

    const exportParams = [];
    if (filter.length > 0) {
      exportParams.push("filter=" + filter.join("|"));
    }
    if (cohortName) {
      exportParams.push("cohortName=" + cohortName);
    }

    let exportUrl = apiBasePath.replace(/^http:/, "https:") + "/export";
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
