import React from "react";
import { withStyles } from "@material-ui/core/styles";
import Typography from "@material-ui/core/Typography";
import "rc-slider/assets/index.css";

import * as Style from "libs/style";
import FacetList from "./FacetList";
import FacetSlider from "./FacetSlider";

const styles = {
  facetCard: {
    ...Style.elements.card,
    margin: "2%",
    paddingBottom: "8px",
    display: "grid",
    // If there is a long word (eg facet name or facet value), break in the
    // middle of the word. Without this, the word stays on one line and its CSS
    // grid is wider than the facet card.
    wordBreak: "break-word",
    height: "200px",
    overflow: "hidden",
    alignContent: "flex-start"
  },
  facetSlider: {
    width: 400,
    margin: 50
  }
};

class FacetCard extends React.Component {
  constructor(props) {
    super(props);
  }

  render() {
    const { classes, facet } = this.props;

    let component;
    if (facet.facet_type === "list") {
      component = (
        <FacetList
          name={facet.db_name}
          values={facet.values}
          selectedValues={this.props.selectedValues}
          listKey={this.props.innerKey}
          updateFacets={this.props.updateFacets}
        />
      );
    } else {
      const { low, high } =
        this.props.selectedValues === undefined
          ? {}
          : this.props.selectedValues;
      component = (
        <FacetSlider
          name={facet.db_name}
          min={facet.min}
          max={facet.max}
          low={low}
          high={high}
          selectedValues={this.props.selectedValues}
          updateFacets={this.props.updateFacets}
        />
      );
    }

    return (
      <div className={classes.facetCard}>
        <div>
          <Typography>{this.props.facet.display_name}</Typography>
        </div>
        {component}
      </div>
    );
  }
}

export default withStyles(styles)(FacetCard);
