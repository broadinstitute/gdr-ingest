import React, { Component } from "react";
import { withStyles } from "@material-ui/core/styles";
import Checkbox from "@material-ui/core/Checkbox";
import {
  AutoSizer,
  CellMeasurer,
  CellMeasurerCache,
  List
} from "react-virtualized";
import ListItem from "@material-ui/core/ListItem";
import ListItemText from "@material-ui/core/ListItemText";
import Typography from "@material-ui/core/Typography";
import { Range } from "rc-slider";

import * as Style from "libs/style";

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
  // By default, each div takes up one grid cell.
  // Don't specify gridColumn, just use default of one cell.
  facetDescription: {
    color: "gray",
    width: "110%"
  },
  facetSearch: {
    margin: "5px 0px 5px 0px",
    border: "0",
    fontSize: "12px",
    width: "100%",
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
  },
  facetSlider: {
    width: 400,
    margin: 50
  }
};

class FacetCard extends Component {
  constructor(props) {
    super(props);

    this.state = {
      searchString: "",
      matchingFacets: props.facet.values,
      listKey: 0
    };

    this.cache = new CellMeasurerCache({
      fixedWidth: true,
      defaultHeight: 25
    });

    this.onClick = this.onClick.bind(this);
    this.isDimmed = this.isDimmed.bind(this);
    this.setSearch = this.setSearch.bind(this);

    this.renderRow = this.renderRow.bind(this);
  }

  render() {
    const { classes, facet } = this.props;

    if (facet.facet_type === "list") {
      return (
        <div className={classes.facetCard}>
          <div>
            <Typography>{this.props.facet.display_name}</Typography>
            <form>
              <input
                className={classes.facetSearch}
                type="text"
                placeholder="Search..."
                ref="filterTextInput"
                onChange={() => this.setSearch()}
              />
            </form>
          </div>
          <AutoSizer>
            {({ width, height }) => {
              return (
                <List
                  key={this.state.listKey}
                  className={classes.facetValueList}
                  width={width}
                  height={height - 50}
                  rowHeight={this.cache.rowHeight}
                  rowRenderer={this.renderRow}
                  rowCount={this.state.matchingFacets.length}
                  overscanRowCount={3}
                />
              );
            }}
          </AutoSizer>
        </div>
      );
    } else {
      const { display_name, min, max } = this.props.facet;
      const diff = max - min;
      const step = diff / 100.0;

      return (
        <div className={classes.facetCard}>
          <div>
            <Typography>{display_name}</Typography>
          </div>
          <div className={classes.facetSlider}>
            <Range
              min={min}
              max={max}
              step={step}
              defaultValue={[min, max]}
              value={[min, max]}
              tabIndex={[0, 1]}
              allowCross={false}
            />
          </div>
        </div>
      );
    }
  }

  renderRow({ index, key, style, parent }) {
    const classes = this.props.classes;
    const value = this.state.matchingFacets[index];
    const cache = this.cache;
    if (value.toLowerCase().includes(this.state.searchString.toLowerCase())) {
      return (
        <CellMeasurer
          key={key}
          cache={cache}
          parent={parent}
          columnIndex={0}
          rowIndex={index}
        >
          <ListItem
            className={classes.facetValue}
            button
            dense
            disableRipple
            onClick={() => this.onClick(value)}
            style={style}
          >
            <Checkbox
              className={classes.facetValueCheckbox}
              checked={
                this.props.selectedValues !== undefined &&
                this.props.selectedValues.includes(value)
              }
            />
            <ListItemText
              className={classes.facetValueNameAndCount}
              classes={{
                primary: this.isDimmed(value) ? classes.grayText : null
              }}
              primary={<div className={classes.facetValueName}>{value}</div>}
            />
          </ListItem>
        </CellMeasurer>
      );
    } else {
      return null;
    }
  }

  isDimmed(facetValue) {
    return (
      this.props.selectedValues !== undefined &&
      this.props.selectedValues.length > 0 &&
      !this.props.selectedValues.includes(facetValue)
    );
  }

  setSearch() {
    const searchString = this.refs.filterTextInput.value;
    const matchingFacets = this.props.facet.values.filter(v =>
      v.toLowerCase().includes(searchString.toLowerCase())
    );
    this.setState({
      searchString: searchString,
      matchingFacets: matchingFacets
    });
  }

  onClick(facetValue) {
    const alreadySelected =
      this.props.selectedValues !== undefined &&
      this.props.selectedValues.includes(facetValue);

    this.props.updateFacets(
      this.props.facet.db_name,
      facetValue,
      !alreadySelected,
      () => this.setState({ listKey: (this.state.listKey + 1) % 10 })
    );
  }
}

export default withStyles(styles)(FacetCard);
