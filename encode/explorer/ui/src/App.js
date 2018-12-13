import React from "react";
import { MuiThemeProvider, createMuiTheme } from "@material-ui/core/styles";
import { ApiClient } from "data_explorer_service";
import FacetsGrid from "components/facets/FacetsGrid";
import Header from "components/Header";
import "App.css";

const theme = createMuiTheme({
  typography: {
    fontFamily: ["Lato", "sans-serif"].join(",")
  },
  palette: {
    primary: {
      main: "rgb(90, 166, 218)",
      contrastText: "#fff"
    },
    secondary: {
      main: "rgb(90, 166, 218)",
      contrastText: "#fff"
    }
  }
});

class App extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      facets: new Map(),
      facetListKeys: new Map(),
      selectedFacetValues: new Map()
    };

    this.apiClient = new ApiClient();
    this.apiClient.basePath = window.location.href.replace(/\/?$/, "") + "/api";

    this.setCountCallback = this.setCountCallback.bind(this);
    this.setFacets = this.setFacets.bind(this);
    this.clearFacets = this.clearFacets.bind(this);
    this.updateFacets = this.updateFacets.bind(this);
  }

  render() {
    const { counts, facets, facetListKeys, selectedFacetValues } = this.state;

    return (
      <MuiThemeProvider theme={theme}>
        <div className="app">
          <FacetsGrid
            apiClient={this.apiClient}
            facets={Array.from(facets.values())}
            setFacets={this.setFacets}
            updateFacets={this.updateFacets}
            selectedFacetValues={selectedFacetValues}
            facetListKeys={facetListKeys}
          />
        </div>
        <div className="headerSearchContainer">
          <Header
            apiClient={this.apiClient}
            counts={counts}
            facets={facets}
            selectedFacetValues={selectedFacetValues}
            clearFacets={this.clearFacets}
            updateFacets={this.updateFacets}
            onMounted={this.setCountCallback}
          />
        </div>
      </MuiThemeProvider>
    );
  }

  setCountCallback(callback) {
    this.countCallback = callback;
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
      this.countCallback
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
      this.countCallback
    );
  }
}

export default App;
