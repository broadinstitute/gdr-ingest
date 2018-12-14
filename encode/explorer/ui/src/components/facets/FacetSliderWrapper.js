import React from "react";
import { withStyles } from "@material-ui/core/styles";

import FacetSlider from "./FacetSlider";

class FacetSliderWrapper extends React.Component {
  constructor(props) {
    super(props);

    const diff = props.max - props.min;
    this.step = Math.pow(10, Math.ceil(Math.log10(diff)) - 2);

    this.effectiveMin = Math.floor(props.min / this.step) * this.step;
    this.effectiveMax = Math.ceil(props.max / this.step) * this.step;
    this.marks = {};
    this.marks[this.effectiveMin] = this.effectiveMin;
    this.marks[this.effectiveMax] = this.effectiveMax;

    this.state = {
      low: this.effectiveMin,
      high: this.effectiveMax
    };

    this.onChange = this.onChange.bind(this);
  }

  render() {
    const { classes, selectedValues } = this.props;
    const { low, high } = this.state;

    return (
      <div>
        <FacetSlider
          effectiveMin={this.effectiveMin}
          effectiveMax={this.effectiveMax}
          low={low}
          high={high}
          selectedValues={selectedValues}
          step={this.step}
          onChange={this.onChange}
          marks={this.marks}
        />
      </div>
    );
  }

  onChange([low, high]) {
    this.setState({ low, high }, () => {
      if (low === this.effectiveMin && high === this.effectiveMax) {
        this.props.updateFacets(this.props.name);
      } else {
        this.props.updateFacets(this.props.name, { low, high });
      }
    });
  }
}

export default FacetSliderWrapper;
