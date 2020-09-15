import React from "react";
import {BrowserRouter as Router, Redirect, Route, Switch} from "react-router-dom";
import "./App.css";
import ErrorDialog from "../components/ErrorDialog";
import Flash from "../components/Flash";
import ProtectedRoute from "../components/ProtectedRoute";
import NotFound from "../pages/NotFound";
import Search from "../pages/Search";
import Detail from "../pages/Detail";
import System from "../pages/System";
import API from "../pages/API";
import ImportMetaData from "../pages/ImportMetaData";
import ServerError from "../pages/ServerError";
import Header from "../components/Header";
import Navigation from "../components/Navigation";
import {configuration, me, reportError} from "../api";
import "../locale/en";
import "../locale/nl";
import Dummy from "./Dummy";
import EduGain from "./EduGain";
import Support from "./Support";
import Scopes from "./Scopes";
import Activity from "./Activity";

const S4 = () => (((1 + Math.random()) * 0x10000) | 0).toString(16).substring(1);

class App extends React.PureComponent {

  constructor(props, context) {
    super(props, context);
    this.state = {
      loading: true,
      currentUser: {},
      configuration: {},
      error: false,
      errorDialogOpen: false,
      errorDialogAction: () => this.setState({errorDialogOpen: false})
    };
    window.onerror = (msg, url, line, col, err) => {
      this.setState({errorDialogOpen: true});
      const info = err || {};
      const response = info.response || {};
      const error = {
        userAgent: navigator.userAgent,
        message: msg,
        url: url,
        line: line,
        col: col,
        error: info.message,
        stack: info.stack,
        targetUrl: response.url,
        status: response.status
      };
      reportError(error);
    };
  }

  handleBackendDown = () => {
    const location = window.location;
    const alreadyRetried = location.href.indexOf("guid") > -1;
    if (alreadyRetried) {
      window.location.href = `${location.protocol}//${location.hostname}${location.port ? ":" + location.port : ""}/error`;
    } else {
      //302 redirects from Shib are cached by the browser. We force a one-time reload
      const guid = (S4() + S4() + "-" + S4() + "-4" + S4().substr(0, 3) + "-" + S4() + "-" + S4() + S4() + S4()).toLowerCase();
      window.location.href = `${location.href}?guid=${guid}`;
    }
  };

  componentDidMount() {
    const location = window.location;
    if (location.href.indexOf("error") > -1) {
      this.setState({loading: false});
    } else {
      me().catch(() => this.handleBackendDown())
        .then(currentUser => {
          if (currentUser && currentUser.uid) {
            configuration().then(configuration =>
              this.setState({loading: false, currentUser: currentUser, configuration: configuration}));
          } else {
            this.handleBackendDown();
          }
        });
    }
  }


  render() {
    const {loading, errorDialogAction, errorDialogOpen} = this.state;

    if (loading) {
      return null; // render null when app is not ready yet
    }

    const {currentUser, configuration} = this.state;

    return (
      <Router>
        <div>
          <div>
            <Flash/>
            <Header currentUser={currentUser}/>
            <Navigation currentUser={currentUser} {...this.props}/>
            <ErrorDialog isOpen={errorDialogOpen}
                         close={errorDialogAction}/>
          </div>
          <Switch>
            <Route exact path="/" render={() => <Redirect to="/search"/>}/>
            <Route path="/search"
                   render={props => <Search currentUser={currentUser}
                                            configuration={configuration} {...props}/>}/>
            <Route path="/metadata/:type/:id/:tab?"
                   render={props => <Detail currentUser={currentUser} fromImport={false}
                                            configuration={configuration} {...props}/>}/>
            <Route path="/clone/:type/:id/:tab?"
                   render={props => <Detail currentUser={currentUser} clone={true} fromImport={false}
                                            configuration={configuration} {...props}/>}/>
            <Route path="/import"
                   render={props => <ImportMetaData currentUser={currentUser}
                                                    configuration={configuration} {...props}/>}/>
            <Route path="/edugain"
                   render={props => <EduGain currentUser={currentUser}
                                             configuration={configuration} {...props}/>}/>
            <Route path="/api"
                   render={props => <API configuration={configuration} {...props}/>}/>

            <ProtectedRoute path="/system"
                            guest={currentUser.guest}
                            render={props => <System currentUser={currentUser}
                                                     configuration={configuration} {...props}/>}/>
            <ProtectedRoute path="/support"
                            guest={currentUser.guest}
                            render={props => <Support {...props}/>}/>

            <ProtectedRoute path="/scopes"
                            guest={currentUser.guest}
                            render={props => <Scopes {...props}/>}/>

            <ProtectedRoute path="/activity"
                            guest={currentUser.guest}
                            render={props => <Activity {...props}/>}/>

            <Route path="/error"
                   render={props => <ServerError {...props}/>}/>

            <Route path="/dummy"
                   render={props => <Dummy {...props}/>}/>

            <Route component={NotFound}/>
          </Switch>
        </div>
      </Router>

    );
  }
}

export default App;
