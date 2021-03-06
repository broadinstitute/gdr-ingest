import React from "react";
import Select from "react-select";

const customStyles = {
  container: (provided, state) => ({
    ...provided,
    fontFamily: ["Lato", "sans-serif"].join(","),
    fontSize: "16px",
    color: "rgb(90, 166, 218)"
  })
};

class Search extends React.Component {
  constructor(props) {
    super(props);
    this.chipsFromSelectedFacetValues = this.chipsFromSelectedFacetValues.bind(
      this
    );
  }

  // renderOption is used to render 1) chip, 2) row in drop-down.
  renderOption = option => {
    // If option.label is set, we are rendering a chip.
    if (option.label != null) {
      return option.label;
    }
    if (option.facetDescription != null) {
      return (
        <div>
          <span style={{ color: "silver" }}>Add</span>
          <span style={{ color: "black" }}> {option.facetName} </span>
          <span style={{ color: "silver" }}>facet with description</span>
          <span style={{ color: "black" }}> {option.facetDescription} </span>
        </div>
      );
    } else {
      return (
        <div>
          <span style={{ color: "silver" }}>Add</span>
          <span style={{ color: "black" }}> {option.facetName} </span>
          <span style={{ color: "silver" }}>facet</span>
        </div>
      );
    }
  };

  renderValue = option => {
    // renderValue is used for autocomplete. If I type "foo" into search box,
    // drop-down options whose renderValue contains "foo" will be shown in the drop-down.
    if (option.value != null) {
      // Chips have a specific value, use that.
      return option.value;
    }
    if (option.facetDescription != null) {
      return option.facetName + " " + option.facetDescription;
    } else {
      return option.facetName;
    }
  };

  chipsFromSelectedFacetValues(selectedFacetValues) {
    let chips = [];
    selectedFacetValues.forEach((values, key) => {
      let facetName = this.props.facets.get(key).display_name;
      if (Array.isArray(values)) {
        if (values.length > 0) {
          for (let value of values) {
            chips.push({
              label: facetName + "=" + value,
              value: key + "=" + value
            });
          }
        }
      } else {
        chips.push({
          label: facetName + "=[" + values.low + "," + values.high + "]",
          value: key + "=[" + values.low + "," + values.high + "]"
        });
      }
    });
    return chips;
  }

  render() {
    const entries = [].concat.apply(
      [],
      Array.from(this.props.selectedFacetValues.values())
    );
    if (entries.length > 0) {
      return (
        <Select
          isMulti="true"
          isSearchable="true"
          onChange={this.props.handleSearchBoxChange}
          value={this.chipsFromSelectedFacetValues(
            this.props.selectedFacetValues
          )}
          styles={customStyles}
          placeholder=""
        />
      );
    } else {
      return (
        <Select
          isMulti="true"
          isDisabled="true"
          onChange={this.props.handleSearchBoxChange}
          value={this.chipsFromSelectedFacetValues(
            this.props.selectedFacetValues
          )}
          styles={customStyles}
          placeholder=""
        />
      );
    }
  }
}

export default Search;
