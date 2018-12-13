import React from "react";
import { withStyles } from "@material-ui/core/styles";
import GridList from "@material-ui/core/GridList";
import GridListTile from "@material-ui/core/GridListTile";
import FacetCard from "components/facets/FacetCard";
import { FacetsApi } from "data_explorer_service";

const styles = {
  gridList: {
    width: "100%",
    height: "100%",
    overflowY: "auto"
  }
};

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
          innerKey={this.props.facetListKeys.get(facet.db_name)}
        />
      </GridListTile>
    ));
    return (
      <GridList
        className={this.props.classes.gridList}
        cols={3}
        cellHeight="auto"
        padding={1}
      >
        {cards}
      </GridList>
    );
  }

  componentDidMount() {
    this.facetsApi.facetsGet(this.facetsCallback);
  }
}

export default withStyles(styles)(FacetsGrid);
