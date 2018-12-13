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
  grayText: {
    color: "silver"
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
    const {
      classes,
      facet,
      innerKey,
      updateFacets,
      selectedValues
    } = this.props;

    let component;
    if (facet.facet_type === "list") {
      component = (
        <FacetList
          name={facet.db_name}
          values={facet.values}
          selectedValues={selectedValues}
          listKey={innerKey}
          updateFacets={updateFacets}
        />
      );
    } else {
      const { low, high } = selectedValues || {};

      component = (
        <FacetSlider
          name={facet.db_name}
          min={facet.min}
          max={facet.max}
          low={low}
          high={high}
          saveChange={this.saveChange}
          onChange={this.onChange}
          updateFacets={updateFacets}
        />
      );
    }

    return (
      <div className={classes.facetCard}>
        <div>
          <Typography>{facet.display_name}</Typography>
        </div>
        {component}
      </div>
    );
  }
}

export default withStyles(styles)(FacetCard);
