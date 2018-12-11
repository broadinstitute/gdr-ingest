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
      facetListKeys: new Map(),
      selectedFacetValues: new Map(),
      counts: null
    };

    this.apiClient = new ApiClient();
    this.apiClient.basePath = window.location.href.replace(/\/?$/, "") + "/api";

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
            facetListKeys={this.state.facetListKeys}
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
    this.setState({
      facets: new Map(facets.map(f => [f.db_name, f])),
      facetListKeys: new Map(facets.map(f => [f.db_name, 0]))
    });
  }

  clearFacets() {
    const newKeys = Array.from(this.state.facetListKeys.entries()).map(
      entry => {
        const selected = this.state.selectedFacetValues.get(entry[0]);
        const newCount =
          selected !== undefined && selected.length > 0
            ? (entry[1] + 1) % 10
            : entry[1];
        return [entry[0], newCount];
      }
    );

    this.setState(
      { selectedFacetValues: new Map(), facetListKeys: new Map(newKeys) },
      () => this.countApi.countGet({}, this.countCallback)
    );
  }

  /**
   * Updates the selection for a single facet value and refreshes the facets data from the server.
   * */
  updateFacets(fieldName, facetValue) {
    const currentFilterMap = new Map(this.state.selectedFacetValues);
    if (facetValue) {
      currentFilterMap.set(fieldName, facetValue);
    } else {
      currentFilterMap.delete(fieldName);
    }

    let currentListKeys = new Map(this.state.facetListKeys);
    let currentListKey = currentListKeys.get(fieldName);
    currentListKeys.set(fieldName, (currentListKey + 1) % 10);

    this.setState(
      { selectedFacetValues: currentFilterMap, facetListKeys: currentListKeys },
      () =>
        this.countApi.countGet(
          { filter: this.filterMapToArray(this.state.selectedFacetValues) },
          this.countCallback
        )
    );
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

export default App;
