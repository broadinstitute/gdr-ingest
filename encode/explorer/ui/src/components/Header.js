import React from "react";
import { withStyles } from "@material-ui/core/styles";
import AppBar from "@material-ui/core/AppBar";
import Toolbar from "@material-ui/core/Toolbar";
import Typography from "@material-ui/core/Typography";
import { DatasetApi } from "data_explorer_service";
import Search from "components/Search";

const styles = {
  appBar: {
    backgroundColor: "#5aa6da"
  },
  datasetName: {
    flexGrow: 1
  },
  totalCountText: {
    marginRight: "60px"
  }
};

class Header extends React.Component {
  constructor(props) {
    super(props);

    this.datasetApi = new DatasetApi(props.apiClient);
    this.datasetCallback = function(error, data) {
      if (error) {
        console.error(error);
      } else {
        this.setState({ datasetName: data.name });
      }
    }.bind(this);

    this.state = {
      datasetName: null
    };

    this.handleSearchBoxChange = this.handleSearchBoxChange.bind(this);
  }

  render() {
    const { classes, counts } = this.props;
    const datasetName = this.state.datasetName;
    const countText =
      counts === null
        ? ""
        : counts.donor_count + " Donors | " + counts.file_count + " Files";

    return (
      <AppBar position="static" className={classes.appBar}>
        <Toolbar>
          <Typography
            className={classes.datasetName}
            variant="headline"
            color="inherit"
            style={{ flexGrow: 1 }}
          >
            {datasetName}
          </Typography>
          <Typography
            className={classes.totalCountText}
            variant="subheading"
            color="inherit"
          >
            {countText}
          </Typography>
        </Toolbar>
        <Search
          searchResults={[]}
          handleSearchBoxChange={this.handleSearchBoxChange}
          selectedFacetValues={this.props.selectedFacetValues}
          facets={this.props.facets}
        />
      </AppBar>
    );
  }

  componentDidMount() {
    this.datasetApi.datasetGet(this.datasetCallback);
  }

  handleSearchBoxChange(selectedOptions, action) {
    if (action.action == "clear") {
      // x on right of search box was clicked.
      this.props.clearFacets();
    } else if (action.action == "remove-value") {
      // chip x was clicked.
      const [fieldName, fieldValue] = action.removedValue.value.split("=");
      const selected = Array.from(
        this.props.selectedFacetValues.get(fieldName)
      );
      selected.splice(selected.indexOf(fieldValue), 1);
      this.props.updateFacets(fieldName, selected);
    }
  }
}

export default withStyles(styles)(Header);
