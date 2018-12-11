/** Export to Terra FAB */

import React from "react";
import { withStyles } from "@material-ui/core/styles";
import CloudUpload from "@material-ui/icons/CloudUpload";
import Button from "@material-ui/core/Button";
import Tooltip from "@material-ui/core/Tooltip";
import TextField from "@material-ui/core/TextField";
import Dialog from "@material-ui/core/Dialog";
import DialogActions from "@material-ui/core/DialogActions";
import DialogContent from "@material-ui/core/DialogContent";

const styles = {
  exportFab: {
    right: "10px",
    top: "3px",
    position: "fixed"
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
    const { classes, donorCount, fileCount } = this.props;
    const totalCount = donorCount + fileCount;
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
        <Button variant="fab" color="secondary" onClick={this.handleClick}>
          <CloudUpload />
        </Button>
      );
    } else {
      button = (
        <Button variant="fab" color="secondary" disabled>
          <CloudUpload />
        </Button>
      );
    }

    return (
      <div>
        {/*
          Style div instead of button itself, to prevent button from moving
          when cohort dialog is shown. See
          https://github.com/mui-org/material-ui/issues/9275#issuecomment-350479467
        */}
        <div className="mui-fixed" className={classes.exportFab}>
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
            <DialogContent>
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
            </DialogActions>
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
    window.location.assign(importBase + encodeURIComponent(encodedExport));
  }
}

export default withStyles(styles)(ExportFab);
