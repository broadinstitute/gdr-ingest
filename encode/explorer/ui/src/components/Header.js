import React from "react";
import { withStyles } from "@material-ui/core/styles";
import AppBar from "@material-ui/core/AppBar";
import Toolbar from "@material-ui/core/Toolbar";
import Typography from "@material-ui/core/Typography";
import { DatasetApi, CountApi } from "data_explorer_service";
import ExportFab from "components/ExportFab";
import Search from "components/Search";

const styles = {
  appBar: {
    backgroundColor: "rgb(90, 166, 218)"
  },
  datasetName: {
    flexGrow: 1
  },
  totalCountText: {
    marginRight: "10px"
  }
};

class Header extends React.Component {
  constructor(props) {
    super(props);

    this.countApi = new CountApi(props.apiClient);
    this.countCallback = function(error, data) {
      if (error) {
        console.error(error);
      } else {
        this.setState({ counts: data });
      }
    }.bind(this);

    this.datasetApi = new DatasetApi(props.apiClient);
    this.datasetCallback = function(error, data) {
      if (error) {
        console.error(error);
      } else {
        this.setState({ datasetName: data.name });
      }
    }.bind(this);

    this.state = {
      datasetName: null,
      counts: null
    };

    this.handleSearchBoxChange = this.handleSearchBoxChange.bind(this);
    this.updateCounts = this.updateCounts.bind(this);
    this.filterMapToArray = this.filterMapToArray.bind(this);
  }

  render() {
    const { apiClient, classes, facets, selectedFacetValues } = this.props;
    const { counts } = this.state;
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
          <ExportFab
            filter={this.filterMapToArray(selectedFacetValues)}
            apiBasePath={apiClient.basePath}
            counts={counts}
          />
        </Toolbar>
        <Search
          searchResults={[]}
          handleSearchBoxChange={this.handleSearchBoxChange}
          selectedFacetValues={selectedFacetValues}
          facets={facets}
        />
      </AppBar>
    );
  }

  componentDidMount() {
    this.datasetApi.datasetGet(this.datasetCallback);
    this.countApi.countGet({}, this.countCallback);
    this.props.onMounted(this.updateCounts);
  }

  handleSearchBoxChange(selectedOptions, action) {
    if (action.action === "clear") {
      // x on right of search box was clicked.
      this.props.clearFacets();
    } else if (action.action === "remove-value") {
      // chip x was clicked.
      const [fieldName, fieldValue] = action.removedValue.value.split("=");
      const selected = Array.from(
        this.props.selectedFacetValues.get(fieldName)
      );
      selected.splice(selected.indexOf(fieldValue), 1);
      this.props.updateFacets(fieldName, selected);
    }
  }

  updateCounts() {
    this.countApi.countGet(
      { filter: this.filterMapToArray(this.props.selectedFacetValues) },
      this.countCallback
    );
  }

  filterMapToArray(filterMap) {
    let filterArray = [];
    filterMap.forEach((values, key) => {
      if (Array.isArray(values)) {
        if (values.length > 0) {
          for (let value of values) {
            filterArray.push(key + "=" + value);
          }
        }
      } else {
        filterArray.push(key + "=" + values.low + "-" + values.high);
      }
    });
    return filterArray;
  }
}

export default withStyles(styles)(Header);
