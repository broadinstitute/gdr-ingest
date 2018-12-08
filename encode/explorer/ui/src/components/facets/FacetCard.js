import React, { Component } from "react";
import { withStyles } from "@material-ui/core/styles";
import Checkbox from "@material-ui/core/Checkbox";
import List from "@material-ui/core/List";
import ListItem from "@material-ui/core/ListItem";
import ListItemText from "@material-ui/core/ListItemText";
import Typography from "@material-ui/core/Typography";

import * as Style from "libs/style";

const styles = {
  facetCard: {
    ...Style.elements.card,
    margin: "2%",
    paddingBottom: "8px",
    display: "grid",
    gridTemplateColumns: "auto 50px",
    // If there is a long word (eg facet name or facet value), break in the
    // middle of the word. Without this, the word stays on one line and its CSS
    // grid is wider than the facet card.
    wordBreak: "break-word",
    height: "200px",
    overflow: "hidden",
    alignContent: "flex-start"
  },
  // By default, each div takes up one grid cell.
  // Don't specify gridColumn, just use default of one cell.
  facetDescription: {
    color: "gray",
    width: "110%"
  },
  facetSearch: {
    margin: "5px 0px 0px 0px",
    border: "0",
    fontSize: "12px",
    width: "110%",
    borderBottom: "2px solid silver",
    outlineWidth: "0"
  },
  facetValueList: {
    gridColumn: "1 / 3",
    margin: "2px 0 0 0",
    maxHeight: "400px",
    overflow: "auto",
    paddingRight: "14px"
  },
  facetValue: {
    // This is a nested div, so need to specify a new grid.
    display: "grid",
    gridTemplateColumns: "24px auto",
    justifyContent: "stretch",
    padding: "0",
    // Disable gray background on ListItem hover.
    "&:hover": {
      backgroundColor: "unset"
    }
  },
  facetValueCheckbox: {
    height: "24px",
    width: "24px"
  },
  facetValueNameAndCount: {
    paddingRight: 0
  },
  facetValueName: {
    // Used by end-to-end tests
  },
  facetValueCount: {
    textAlign: "right"
  },
  grayText: {
    color: "silver"
  }
};

class FacetCard extends Component {
  constructor(props) {
    super(props);

    this.state = {
      searchString: ""
    };

    this.onClick = this.onClick.bind(this);
    this.isDimmed = this.isDimmed.bind(this);
    this.setSearch = this.setSearch.bind(this);
  }

  render() {
    const { classes } = this.props;

    const facetValues = this.props.facet.values;

    if (facetValues.value_type === "list") {
      const facetValueDivs = facetValues.values
        .filter(facetValue =>
          facetValue.name
            .toLowerCase()
            .includes(this.state.searchString.toLowerCase())
        )
        .map(value => (
          <ListItem
            className={classes.facetValue}
            key={value.name}
            button
            dense
            disableRipple
            onClick={e => this.onClick(value.name)}
          >
            <Checkbox
              className={classes.facetValueCheckbox}
              checked={
                this.props.selectedValues != null &&
                this.props.selectedValues.includes(value.name)
              }
            />
            <ListItemText
              className={classes.facetValueNameAndCount}
              classes={{
                primary: this.isDimmed(value) ? classes.grayText : null
              }}
              primary={
                <div
                  style={{ display: "grid", gridTemplateColumns: "auto 50px" }}
                >
                  <div className={classes.facetValueName}>{value.name}</div>
                  <div className={classes.facetValueCount}>{value.count}</div>
                </div>
              }
            />
          </ListItem>
        ));

      return (
        <div className={classes.facetCard}>
          <div>
            <Typography>{this.props.facet.name}</Typography>
            <Typography className={classes.facetDescription}>
              {this.props.facet.description}
            </Typography>
            <Typography>
              <form>
                <input
                  className={classes.facetSearch}
                  type="text"
                  placeholder="Search..."
                  ref="filterTextInput"
                  onChange={() => this.setSearch()}
                />
              </form>
            </Typography>
          </div>
          <List dense className={classes.facetValueList}>
            {facetValueDivs}
          </List>
        </div>
      );
    } else {
      return (
        <div className={classes.facetCard}>
          <div>
            <Typography>{this.props.facet.name}</Typography>
            <Typography className={classes.facetDescription}>
              {this.props.facet.description}
            </Typography>
          </div>
        </div>
      );
    }
  }

  isDimmed(facetValue) {
    return (
      this.props.selectedValues != null &&
      this.props.selectedValues.length > 0 &&
      !this.props.selectedValues.includes(facetValue.name)
    );
  }

  setSearch() {
    this.setState({ searchString: this.refs.filterTextInput.value });
  }

  onClick(facetValue) {
    // facetValue is a string, eg "female"
    let isSelected;
    if (
      this.props.selectedValues != null &&
      this.props.selectedValues.length > 0 &&
      this.props.selectedValues.includes(facetValue)
    ) {
      // User must have unchecked the checkbox.
      isSelected = false;
    } else {
      // User must have checked the checkbox.
      isSelected = true;
    }

    this.props.updateFacets(this.props.facet.db_name, facetValue, isSelected);
  }
}

export default withStyles(styles)(FacetCard);
