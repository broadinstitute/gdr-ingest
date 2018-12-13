import React from "react";
import { withStyles } from "@material-ui/core/styles";
import Slider, { Range } from "rc-slider";
import "rc-slider/assets/index.css";

const styles = {
  facetSlider: {
    width: 400,
    margin: 75
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

    this.state = {
      low: this.effectiveMin,
      high: this.effectiveMax
    };

    this.onChange = this.onChange.bind(this);
    this.saveChange = this.saveChange.bind(this);

    const createSliderWithTooltip = Slider.createSliderWithTooltip;
    this.Range = createSliderWithTooltip(Range);
  }

  render() {
    const { classes } = this.props;
    const { low, high } = this.state;

    return (
      <div className={classes.facetSlider}>
        <this.Range
          min={this.effectiveMin}
          max={this.effectiveMax}
          step={this.step}
          value={[low, high]}
          allowCross={false}
          onChange={this.onChange}
          onAfterChange={this.saveChange}
          marks={this.marks}
        />
      </div>
    );
  }

  onChange([low, high]) {
    this.setState({ low: low, high: high });
  }

  saveChange([low, high]) {
    this.setState({ low: low, high: high }, () => {
      const updated =
        low === this.effectiveMin && high === this.effectiveMax
          ? null
          : { low: low, high: high };

      this.props.updateFacets(this.props.name, updated);
    });
  }
}

export default withStyles(styles)(FacetSlider);