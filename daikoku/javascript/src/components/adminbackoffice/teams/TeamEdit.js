import React, { Component } from 'react';
import { connect } from 'react-redux';

import * as Services from '../../../services';

import { UserBackOffice } from '../../backoffice';
import { AvatarChooser, Can, manage, daikoku, Spinner } from '../../utils';
import { t, Translation } from '../../../locales';

const LazyForm = React.lazy(() => import('../../inputs/Form'));

class TeamEditForAdministrationComponent extends Component {
  state = {
    team: null,
  };

  flow =
    window.location.pathname.indexOf('/edition') === -1
      ? ['_id', '_tenant', 'name', 'description', 'contact', 'avatar', 'avatarFrom', 'metadata']
      : ['_id', 'name', 'description', 'contact', 'avatar', 'avatarFrom', 'metadata'];

  schema = {
    _id: {
      type: 'string',
      props: { label: 'Id', disabled: true },
    },
    _tenant: {
      type: 'select',
      props: {
        label: t('Tenant', this.props.currentLanguage),
        valuesFrom: '/api/tenants',
        transformer: tenant => ({ label: tenant.name, value: tenant._id }),
      },
    },
    type: {
      type: 'select',
      props: {
        label: t('Type', this.props.currrentLanguage),
        possibleValues: [
          { label: 'Personal', value: 'Personal' },
          { label: 'Organization', value: 'Organization' },
        ],
      },
    },
    name: {
      type: 'string',
      props: { label: t('Name', this.props.currentLanguage) },
    },
    description: {
      type: 'string',
      props: { label: t('Description', this.props.currentLanguage) },
    },
    contact: {
      type: 'string',
      props: { label: t('Team contact', this.props.currentLanguage) },
    },
    avatar: {
      type: 'string',
      props: { label: t('Team avatar', this.props.currentLanguage) },
    },
    avatarFrom: {
      type: AvatarChooser,
      props: {
        team: () => this.state.team,
        currentLanguage: this.props.currentLanguage,
      },
    },
    metadata: {
      type: 'object',
      visible: () => window.location.pathname.indexOf('/edition') === -1,
      props: {
        label: t('Metadata', this.props.currentLanguage),
      },
    },
  };

  save = () => {
    if (this.props.location && this.props.location.state && this.props.location.state.newTeam) {
      Services.createTeam(this.state.team).then(team => {
        this.setState({ team });
        window.location.reload();
      });
    } else {
      Services.updateTeam(this.state.team).then(team => this.setState({ team }));
    }
  };

  members = () => {
    this.props.history.push(`/${this.state.team._humanReadableId}/settings/members`);
  };

  componentDidMount() {
    if (this.props.location && this.props.location.state && this.props.location.state.newTeam) {
      this.setState({ team: this.props.location.state.newTeam, create: true });
    } else {
      Services.teamFull(this.props.match.params.teamSettingId).then(team =>
        this.setState({ team })
      );
    }
  }

  render() {
    if (!this.state.team) {
      return null;
    }

    return (
      <UserBackOffice tab="Teams">
        <Can I={manage} a={daikoku} dispatchError>
          <div className="row d-flex justify-content-start align-items-center">
            {this.state.team && (
              <div
                style={{
                  width: '100px',
                  height: '100px',
                  borderRadius: '50px',
                  border: '3px solid #fff',
                  boxShadow: '0px 0px 0px 3px lightgrey',
                  display: 'flex',
                  justifyContent: 'flex-end',
                  alignItems: 'center',
                }}>
                <img
                  src={this.state.team.avatar}
                  style={{ width: 200, borderRadius: '50%', backgroundColor: 'white' }}
                  alt="avatar"
                />
              </div>
            )}
            <h1 className="h1-rwd-reduce ml-2">Team - {this.state.team.name}</h1>
          </div>
          <div className="row">
            <React.Suspense fallback={<Spinner />}>
              <LazyForm
                flow={this.flow}
                schema={this.schema}
                value={this.state.team}
                onChange={team => this.setState({ team })}
                style={{ marginBottom: 100 }}
              />
            </React.Suspense>
            <div style={{ height: 60 }} />
            <div className="row form-back-fixedBtns">
              <a
                className="btn btn-outline-primary"
                href="#"
                onClick={() => this.props.history.goBack()}>
                <i className="fas fa-chevron-left mr-1" />
                <Translation i18nkey="Back" language={this.props.currentLanguage}>
                  Back
                </Translation>
              </a>
              <button
                style={{ marginLeft: 5 }}
                type="button"
                className="btn btn-outline-primary"
                disabled={this.state.create}
                onClick={this.members}>
                <span>
                  <i className="fas fa-users mr-1" />
                  <Translation i18nkey="members" language={this.props.currentLanguage} isPlural>
                    Members
                  </Translation>
                </span>
              </button>
              <button
                style={{ marginLeft: 5 }}
                type="button"
                className="btn btn-outline-success"
                onClick={this.save}>
                {!this.state.create && (
                  <span>
                    <i className="fas fa-save mr-1" />
                    <Translation i18nkey="Save" language={this.props.currentLanguage}>
                      Save
                    </Translation>
                  </span>
                )}
                {this.state.create && (
                  <span>
                    <i className="fas fa-save mr-1" />
                    <Translation i18nkey="Create" language={this.props.currentLanguage}>
                      Create
                    </Translation>
                  </span>
                )}
              </button>
            </div>
          </div>
        </Can>
      </UserBackOffice>
    );
  }
}

const mapStateToProps = state => ({
  ...state.context,
});

export const TeamEditForAdmin = connect(mapStateToProps)(TeamEditForAdministrationComponent);
