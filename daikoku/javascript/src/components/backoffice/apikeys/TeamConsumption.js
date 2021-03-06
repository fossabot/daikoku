import React, { Component } from 'react';
import { connect } from 'react-redux';
import * as _ from 'lodash';

import { OtoroshiStatsVizualization } from '../../utils';
import { TeamBackOffice } from '../TeamBackOffice';
import * as Services from '../../../services';

class TeamConsumptionComponent extends Component {
  mappers = [
    {
      type: 'DoubleRoundChart',
      label: 'Hits by api/plan',
      title: 'Hits by api/plan',
      formatter: data =>
        _.sortBy(
          data.reduce((acc, item) => {
            const value = acc.find(a => a.name === item.apiName) || { count: 0 };
            return [
              ...acc.filter(a => a.name !== item.apiName),
              { name: item.apiName, count: value.count + item.hits },
            ];
          }, []),
          ['name']
        ),
      formatter2: data =>
        _.sortBy(
          data.reduce((acc, item) => {
            const plan = `${item.apiName} - ${item.plan}`;
            const value = acc.find(a => a.name === plan) || { count: 0 };
            return [
              ...acc.filter(a => a.name !== plan),
              { name: plan, api: item.apiName, count: value.count + item.hits },
            ];
          }, []),
          ['api']
        ),
      dataKey: 'count',
      parentKey: 'api',
    },
  ];

  render() {
    return (
      <TeamBackOffice tab="ApiKeys">
        <div className="row">
          <div className="col">
            <h1>Consumption</h1>
            <OtoroshiStatsVizualization
              sync={() => Services.syncTeamBilling(this.props.currentTeam._id)}
              fetchData={(from, to) =>
                Services.getTeamConsumptions(
                  this.props.currentTeam._id,
                  from.valueOf(),
                  to.valueOf()
                )
              }
              mappers={this.mappers}
            />
          </div>
        </div>
      </TeamBackOffice>
    );
  }
}

const mapStateToProps = state => ({
  ...state.context,
});

export const TeamConsumption = connect(mapStateToProps)(TeamConsumptionComponent);
