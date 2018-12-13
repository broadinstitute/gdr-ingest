import React from "react";
import { withStyles } from "@material-ui/core/styles";
import Slider, { Range } from "rc-slider";
import "rc-slider/assets/index.css";

const styles = {
  facetSlider: {
    margin: "10%"
  }
};

class FacetSlider extends React.Component {
  constructor(props) {
    super(props);

    const diff = props.max - props.min;
    this.step = Math.pow(10, Math.ceil(Math.log10(diff)) - 2);

    this.effectiveMin = Math.floor(props.min / this.step) * this.step;
    this.effectiveMax = Math.ceil(props.max / this.step) * this.step;
    this.marks = {};
    this.marks[this.effectiveMin] = this.effectiveMin;
    this.marks[this.effectiveMax] = this.effectiveMax;

    this.onChange = this.onChange.bind(this);
    const createSliderWithTooltip = Slider.createSliderWithTooltip;
    this.Range = createSliderWithTooltip(Range);
  }

  render() {
    const { classes, low, high } = this.props;

    return (
      <div className={classes.facetSlider}>
        <this.Range
          min={this.effectiveMin}
          max={this.effectiveMax}
          step={this.step}
          value={[low || this.effectiveMin, high || this.effectiveMax]}
          allowCross={false}
          onChange={this.onChange}
          marks={this.marks}
        />
      </div>
    );
  }

  onChange([low, high]) {
    const { name, updateFacets } = this.props;

    if (low === this.effectiveMin && high === this.effectiveMax) {
      updateFacets(name, null);
    } else {
      updateFacets(name, { low, high });
    }
  }
}

export default withStyles(styles)(FacetSlider);
