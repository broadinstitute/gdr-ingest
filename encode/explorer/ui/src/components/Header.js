import React from "react";
import AppBar from "@material-ui/core/AppBar";
import Toolbar from "@material-ui/core/Toolbar";
import Typography from "@material-ui/core/Typography";
import { DatasetApi } from "data_explorer_service";
import Search from "components/Search";

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
    const datasetName = this.state.datasetName;
    const counts = this.props.counts;
    const countText =
      counts === null
        ? ""
        : counts.donor_count + " Donors, " + counts.file_count + " Files";

    return (
      <AppBar position="static" style={{ backgroundColor: "#5aa6da" }}>
        <Toolbar>
          <Typography
            className="datasetName"
            variant="headline"
            color="inherit"
            style={{ flexGrow: 1 }}
          >
            {datasetName}
          </Typography>
          <Typography className="totalCountText" color="inherit">
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
      let parts = action.removedValue.value.split("=");
      this.props.updateFacets(parts[0], parts[1], false);
    }
  }
}

export default Header;
