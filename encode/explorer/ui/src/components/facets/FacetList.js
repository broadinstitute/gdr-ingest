import React from "react";
import { withStyles } from "@material-ui/core/styles";
import Checkbox from "@material-ui/core/Checkbox";
import ListItem from "@material-ui/core/ListItem";
import ListItemText from "@material-ui/core/ListItemText";
import {
  AutoSizer,
  CellMeasurer,
  CellMeasurerCache,
  List
} from "react-virtualized";

const styles = {
  facetSearch: {
    margin: "5px 0px 5px 0px",
    border: "0",
    fontSize: "12px",
    width: "100%",
    borderBottom: "2px solid silver",
    outlineWidth: "0"
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
  facetValueList: {
    gridColumn: "1 / 3",
    margin: "2px 0 0 0",
    maxHeight: "400px",
    overflow: "auto",
    paddingRight: "14px"
  },
  facetValueCheckbox: {
    height: "24px",
    width: "24px"
  },
  facetValueNameAndCount: {
    paddingRight: 0
  }
};

class FacetList extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      searchString: "",
      matchingFacets: this.props.values
    };

    this.cache = new CellMeasurerCache({
      fixedWidth: true,
      defaultHeight: 25
    });

    this.isDimmed = this.isDimmed.bind(this);
    this.setSearch = this.setSearch.bind(this);
    this.onClick = this.onClick.bind(this);
    this.renderRow = this.renderRow.bind(this);
  }

  render() {
    const { classes } = this.props;
    return (
      <div style={{ height: "145px" }}>
        <form>
          <input
            className={classes.facetSearch}
            type="text"
            placeholder="Search..."
            ref="filterTextInput"
            onChange={() => this.setSearch()}
          />
        </form>
        <AutoSizer>
          {({ width, height }) => {
            return (
              <List
                key={this.props.listKey}
                className={classes.facetValueList}
                width={width}
                height={height}
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
  }

  renderRow({ index, key, style, parent }) {
    const classes = this.props.classes;
    const value = this.state.matchingFacets[index];
    const cache = this.cache;
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
    const matchingFacets = this.props.values.filter(v =>
      v.toLowerCase().includes(searchString.toLowerCase())
    );
    this.setState({
      searchString: searchString,
      matchingFacets: matchingFacets
    });
  }

  onClick(facetValue) {
    const currentSelection =
      this.props.selectedValues === undefined
        ? []
        : Array.from(this.props.selectedValues);
    const valueIndex = currentSelection.indexOf(facetValue);

    if (valueIndex >= 0) {
      currentSelection.splice(valueIndex, 1);
    } else {
      currentSelection.push(facetValue);
    }

    this.props.updateFacets(this.props.name, currentSelection);
  }
}

export default withStyles(styles)(FacetList);
