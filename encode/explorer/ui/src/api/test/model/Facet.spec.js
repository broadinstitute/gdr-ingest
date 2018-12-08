/**
 * Data Explorer Service
 * API Service that reads from Elasticsearch.
 *
 * OpenAPI spec version: 0.0.1
 *
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 *
 */

(function(root, factory) {
  if (typeof define === "function" && define.amd) {
    // AMD.
    define(["expect.js", "../../src/index"], factory);
  } else if (typeof module === "object" && module.exports) {
    // CommonJS-like environments that support module.exports, like Node.
    factory(require("expect.js"), require("../../src/index"));
  } else {
    // Browser globals (root is window)
    factory(root.expect, root.DataExplorerService);
  }
})(this, function(expect, DataExplorerService) {
  "use strict";

  var instance;

  beforeEach(function() {
    instance = new DataExplorerService.Facet();
  });

  var getProperty = function(object, getter, property) {
    // Use getter method if present; otherwise, get the property directly.
    if (typeof object[getter] === "function") return object[getter]();
    else return object[property];
  };

  var setProperty = function(object, setter, property, value) {
    // Use setter method if present; otherwise, set the property directly.
    if (typeof object[setter] === "function") object[setter](value);
    else object[property] = value;
  };

  describe("Facet", function() {
    it("should create an instance of Facet", function() {
      // uncomment below and update the code to test Facet
      //var instane = new DataExplorerService.Facet();
      //expect(instance).to.be.a(DataExplorerService.Facet);
    });

    it('should have the property name (base name: "name")', function() {
      // uncomment below and update the code to test the property name
      //var instane = new DataExplorerService.Facet();
      //expect(instance).to.be();
    });

    it('should have the property description (base name: "description")', function() {
      // uncomment below and update the code to test the property description
      //var instane = new DataExplorerService.Facet();
      //expect(instance).to.be();
    });

    it('should have the property dbName (base name: "db_name")', function() {
      // uncomment below and update the code to test the property dbName
      //var instane = new DataExplorerService.Facet();
      //expect(instance).to.be();
    });
  });
});
