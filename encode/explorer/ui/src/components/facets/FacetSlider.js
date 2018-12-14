import React from "react";
import { withStyles } from "@material-ui/core/styles";
import Slider, { Range } from "rc-slider";
import "rc-slider/assets/index.css";
import "components/facets/FacetsSlider.css";

const styles = {
  facetSlider: {
    margin: "10%"
  }
};

class FacetSlider extends React.Component {
  constructor(props) {
    super(props);
    const diff = props.max - props.min;
    const createSliderWithTooltip = Slider.createSliderWithTooltip;
    this.Range = createSliderWithTooltip(Range);
  }

  render() {
    const {
      classes,
      high,
      low,
      marks,
      onChange,
      selectedValues,
      step
    } = this.props;

    let testlow = low;
    let testhigh = high;

    if (selectedValues && selectedValues.length === 0) {
      testlow = this.props.effectiveMin;
      testhigh = this.props.effectiveMax;
    }

    return (
      <div className={classes.facetSlider}>
        <this.Range
          min={this.props.effectiveMin}
          max={this.props.effectiveMax}
          step={step}
          value={[testlow, testhigh]}
          allowCross={false}
          onChange={this.props.onChange}
          marks={marks}
          className={classes.rcSlider}
        />
      </div>
    );
  }
}

export default withStyles(styles)(FacetSlider);
