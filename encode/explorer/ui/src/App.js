import React from "react";
import { MuiThemeProvider, createMuiTheme } from "@material-ui/core/styles";

import "App.css";
import { ApiClient, CountApi } from "data_explorer_service";
import ExportFab from "components/ExportFab";
import FacetsGrid from "components/facets/FacetsGrid";
import Header from "components/Header";

const theme = createMuiTheme({
  typography: {
    fontFamily: ["Lato", "sans-serif"].join(",")
  }
});

class App extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      facets: new Map(),
      selectedFacetValues: new Map(),
      counts: null
    };

    this.apiClient = new ApiClient();
    // FIXME: DON'T CHECK THIS IN
    this.apiClient.basePath = "http://localhost:8080/api";
    //this.apiClient.basePath = "/api";

    this.countApi = new CountApi(this.apiClient);
    this.countCallback = function(error, data) {
      if (error) {
        console.error(error);
      } else {
        this.setState({ counts: data });
      }
    }.bind(this);

    this.setFacets = this.setFacets.bind(this);
    this.clearFacets = this.clearFacets.bind(this);
    this.updateFacets = this.updateFacets.bind(this);
  }

  render() {
    return (
      <MuiThemeProvider theme={theme}>
        <div className="app">
          <FacetsGrid
            apiClient={this.apiClient}
            facets={Array.from(this.state.facets.values())}
            setFacets={this.setFacets}
            updateFacets={this.updateFacets}
            selectedFacetValues={this.state.selectedFacetValues}
          />
        </div>
        <div className="headerSearchContainer">
          <Header
            apiClient={this.apiClient}
            counts={this.state.counts}
            facets={this.state.facets}
            selectedFacetValues={this.state.selectedFacetValues}
            clearFacets={this.clearFacets}
            updateFacets={this.updateFacets}
          />
        </div>
        <ExportFab
          filter={this.filterMapToArray(this.state.selectedFacetValues)}
          apiBasePath={this.apiClient.basePath}
        />
      </MuiThemeProvider>
    );
  }

  componentDidMount() {
    this.countApi.countGet({}, this.countCallback);
  }

  setFacets(facets) {
    this.setState({ facets: new Map(facets.map(f => [f.db_name, f])) });
  }

  clearFacets() {
    this.setState({ selectedFacetValues: new Map() });
  }

  /**
   * Updates the selection for a single facet value and refreshes the facets data from the server.
   * */
  updateFacets(fieldName, facetValue, isSelected, callback) {
    let currentFilterMap = new Map(this.state.selectedFacetValues);
    let currentFacetValues = currentFilterMap.get(fieldName);
    if (isSelected) {
      // Add facetValue to the list of filters for facetName
      if (currentFacetValues === undefined) {
        currentFilterMap.set(fieldName, [facetValue]);
      } else {
        currentFacetValues.push(facetValue);
      }
    } else {
      // Remove facetValue from the list of filters for facetName
      currentFilterMap.set(
        fieldName,
        currentFacetValues.filter(v => v !== facetValue)
      );
    }

    this.countApi.countGet(
      { filter: this.filterMapToArray(currentFilterMap) },
      this.countCallback
    );

    this.setState({ selectedFacetValues: currentFilterMap }, callback);
  }

  /**
   * Converts a Map of filters to an Array of filter strings interpretable by
   * the backend
   * @param filterMap Map of esFieldName:[facetValues] pairs
   * @return [string] Array of "fieldName=facetValue" strings
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
