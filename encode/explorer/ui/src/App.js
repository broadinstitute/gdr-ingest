import React, { Component } from "react";
import { MuiThemeProvider, createMuiTheme } from "@material-ui/core/styles";

import "App.css";
import { ApiClient, DatasetApi, FacetsApi } from "data_explorer_service";
import ExportFab from "components/ExportFab";
import ExportUrlApi from "api/src/api/ExportUrlApi";
import FacetsGrid from "components/facets/FacetsGrid";
import Search from "components/Search";
import Header from "components/Header";

const theme = createMuiTheme({
  typography: {
    fontFamily: ["Lato", "sans-serif"].join(",")
  }
});

class App extends Component {
  constructor(props) {
    super(props);
    this.state = {
      datasetName: "",
      // What to show in search box by default. If this is the empty string, the
      // react-select default of "Select..." is shown.
      searchPlaceholderText: "",
      // Map from db_name to facet data returned from API server /facets call.
      facets: new Map(),
      totalCount: null,
      // Map from db_name to a list of selected facet values.
      selectedFacetValues: new Map()
    };

    this.apiClient = new ApiClient();
    // FIXME: DON'T CHECK THIS IN
    this.apiClient.basePath = "http://localhost:8080/api";
    //this.apiClient.basePath = "/api";
    this.facetsApi = new FacetsApi(this.apiClient);
    this.facetsCallback = function(error, data) {
      if (error) {
        console.error(error);
        // TODO(alanhwang): Redirect to an error page
      } else {
        this.setState({
          facets: this.getFacetMap(data.facets),
          totalCount: data.count
        });
      }
    }.bind(this);

    // Map from facet name to a list of facet values.
    this.filterMap = new Map();
    this.updateFacets = this.updateFacets.bind(this);
    this.handleSearchBoxChange = this.handleSearchBoxChange.bind(this);
  }

  render() {
    return (
      <MuiThemeProvider theme={theme}>
        <div className="app">
          <FacetsGrid
            updateFacets={this.updateFacets}
            selectedFacetValues={this.state.selectedFacetValues}
            facets={Array.from(this.state.facets.values())}
          />
          <ExportFab
            exportUrlApi={new ExportUrlApi(this.apiClient)}
            filter={this.filterMapToArray(this.state.selectedFacetValues)}
          />
        </div>
        <div className="headerSearchContainer">
          <Header
            datasetName={this.state.datasetName}
            totalCount={this.state.totalCount}
          />
          <Search
            searchPlaceholderText=""
            searchResults={this.state.searchResults}
            handleSearchBoxChange={this.handleSearchBoxChange}
            selectedFacetValues={this.state.selectedFacetValues}
            facets={this.state.facets}
          />
        </div>
      </MuiThemeProvider>
    );
  }

  componentDidMount() {
    this.facetsApi.facetsGet({}, this.facetsCallback);

    // Call /api/dataset
    let datasetApi = new DatasetApi(this.apiClient);
    let datasetCallback = function(error, data) {
      if (error) {
        // TODO: Show error in snackbar.
        console.error(error);
      } else {
        this.setState({
          datasetName: data.name,
          searchPlaceholderText: data.search_placeholder_text
        });
      }
    }.bind(this);
    datasetApi.datasetGet(datasetCallback);
  }

  getFacetMap(facets) {
    var facetMap = new Map();
    facets.forEach(function(facet) {
      facetMap.set(facet.db_name, facet);
    });
    return facetMap;
  }

  handleSearchBoxChange(selectedOptions, action) {
    if (action.action == "clear") {
      // x on right of search box was clicked.
      this.setState({ selectedFacetValues: new Map() });
      this.facetsApi.facetsGet(
        {
          filter: this.filterMapToArray(new Map()),
          extraFacets: this.state.extraFacetEsFieldNames
        },
        this.facetsCallback
      );
    } else if (action.action == "remove-value") {
      // chip x was clicked.
      let parts = action.removedValue.value.split("=");
      this.updateFacets(parts[0], parts[1], false);
    } else if (action.action == "select-option") {
      // Drop-down row was clicked.
      let newExtraFacetEsFieldNames = this.state.extraFacetEsFieldNames;
      newExtraFacetEsFieldNames.push(action.option.esFieldName);
      this.setState({ extraFacetEsFieldNames: newExtraFacetEsFieldNames });
      this.facetsApi.facetsGet(
        {
          filter: this.filterMapToArray(this.state.selectedFacetValues),
          extraFacets: newExtraFacetEsFieldNames
        },
        this.facetsCallback
      );
    }
  }

  /**
   * Updates the selection for a single facet value and refreshes the facets data from the server.
   * */
  updateFacets(esFieldName, facetValue, isSelected) {
    let currentFilterMap = this.state.selectedFacetValues;
    let currentFacetValues = currentFilterMap.get(esFieldName);
    if (isSelected) {
      // Add facetValue to the list of filters for facetName
      if (currentFacetValues === undefined) {
        currentFilterMap.set(esFieldName, [facetValue]);
      } else {
        currentFacetValues.push(facetValue);
      }
    } else if (currentFilterMap.get(esFieldName) !== undefined) {
      // Remove facetValue from the list of filters for facetName
      currentFilterMap.set(
        esFieldName,
        this.removeFacetValue(currentFacetValues, facetValue)
      );
    }
    // Update the state
    this.setState({ selectedFacetValues: currentFilterMap });

    // Update the facets grid.
    this.facetsApi.facetsGet(
      {
        filter: this.filterMapToArray(this.state.selectedFacetValues),
        extraFacets: this.state.extraFacetEsFieldNames
      },
      this.facetsCallback
    );
  }

  // Remove the given facet value from a list of facet values.
  removeFacetValue(valueList, facetValue) {
    let newValueList = [];
    for (let i = 0; i < valueList.length; i++) {
      if (valueList[i] !== facetValue) {
        newValueList.push(valueList[i]);
      }
    }
    return newValueList;
  }

  /**
   * Converts a Map of filters to an Array of filter strings interpretable by
   * the backend
   * @param filterMap Map of esFieldName:[facetValues] pairs
   * @return [string] Array of "esFieldName=facetValue" strings
   */
  filterMapToArray(filterMap) {
    let filterArray = [];
    filterMap.forEach((values, key) => {
      // Scenario where there are no values for a key: A single value is
      // checked for a facet. The value is unchecked. The facet name will
      // still be a key in filterMap, but there will be no values.
      if (values.length > 0) {
        for (let value of values) {
          filterArray.push(key + "=" + value);
        }
      }
    });
    return filterArray;
  }
}

export default App;
