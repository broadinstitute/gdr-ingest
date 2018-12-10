import React from "react";
import GridList from "@material-ui/core/GridList";
import GridListTile from "@material-ui/core/GridListTile";

import "components/facets/FacetsGrid.css";
import FacetCard from "components/facets/FacetCard";
import { FacetsApi } from "data_explorer_service";

class FacetsGrid extends React.Component {
  constructor(props) {
    super(props);

    this.facetsApi = new FacetsApi(props.apiClient);
    this.facetsCallback = function(error, data) {
      if (error) {
        console.error(error);
      } else {
        this.props.setFacets(data.facets);
      }
    }.bind(this);
  }

  render() {
    const cards = this.props.facets.map(facet => (
      <GridListTile key={facet.db_name}>
        <FacetCard
          facet={facet}
          selectedValues={this.props.selectedFacetValues.get(facet.db_name)}
          updateFacets={this.props.updateFacets}
        />
      </GridListTile>
    ));
    return (
      <GridList className="gridList" cols={3} cellHeight="auto" padding={1}>
        {cards}
      </GridList>
    );
  }

  componentDidMount() {
    this.facetsApi.facetsGet(this.facetsCallback);
  }
}

export default FacetsGrid;
