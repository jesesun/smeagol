//@flow
import React from 'react';
import injectSheet from 'react-jss';
import ActionLink from './ActionLink';
import ActionButton from './ActionButton';
import PageNameForm from './PageNameForm';
import { withRouter } from 'react-router-dom';
import classNames from 'classnames';
import SearchBar from "./SearchBar";
import ConfirmModal from "./ConfirmModal";

const styles = {
    header: {
        borderBottom: '1px solid #ddd'
    },
    actions: {
        marginBottom: '1em'
    }
};

type Props = {
    page: any,
    wiki: any,
    pagesLink: string,
    historyLink: string,
    onDelete: () => void,
    onHomeClick: () => void,
    onOkMoveClick: () => void,
    onRestoreClick: () => void,
    search: (string) => void,
    history: any,
    classes: any
}

type State = {
    showCreateForm: boolean,
    showMoveForm: boolean,
    showDeleteConfirm: boolean
};

class PageHeader extends React.Component<Props,State> {

    constructor(props) {
        super(props);
        this.state =  {
            showCreateForm: false
        };
    }

    onCreateClick = () =>  {
        this.setState({
            showCreateForm: true
        });
    };

    onAbortCreateClick = () => {
        this.setState({
            showCreateForm: false
        });
    };

    onOkMoveClick = (name) => {
        const path = this.getPathFromPagename(name);
        this.props.onOkMoveClick(path);
    };

    onDeleteClick = () => {
        this.setState({
            showDeleteConfirm: true
        });
    };

    onAbortDeleteClick = () => {
        this.setState({
            showDeleteConfirm: false
        });
    };

    onOkCreate = (name) => {
        const { repository, branch } = this.props.wiki;
        let wikiPath = `/${repository}/${branch}/`;
        const pagePath = this.getPathFromPagename(name);

        this.props.history.push(wikiPath + pagePath);
    };

    getPathFromPagename = (name) => {
        if (name.startsWith('/')) {
            return name.substr(1);
        } else {
            return `${this.props.wiki.directory}/${name}`;
        }
    };

    onMoveClick = () => {
        this.setState({
            showMoveForm: true
        });
    };

    onAbortMoveClick = () => {
        this.setState({
            showMoveForm: false
        });
    };

    onRestoreClick = () => {
        const pagePath = this.props.page.path;
        const commit = this.props.page.commit.commitId;
        this.props.onRestoreClick(pagePath, commit);
    };

    getPagePathWithoutRootDirectory(page, wiki) {
        if ( page.path.indexOf(wiki.directory) === 0 ) {
            return page.path.substring(wiki.directory.length + 1);
        }

        return page.path;
    }

    render() {
        const { page, wiki, pagesLink, classes, onDelete, onHomeClick, historyLink, search } = this.props;

        const pathWithoutRoot = this.getPagePathWithoutRootDirectory(page, wiki);

        const homeButton = <ActionButton onClick={onHomeClick}  i18nKey="page-header_home" type="primary" />;
        const createButton = <ActionButton onClick={this.onCreateClick} i18nKey="page-header_create" type="primary" />;
        const pagesButton = <ActionLink to={ pagesLink }  i18nKey="page-header_pages" type="primary" />;
        const historyButton = <ActionLink to={ historyLink }  i18nKey="page-header_history" type="primary" />;
        const edit = page._links.edit ? <ActionLink to="?edit=true" i18nKey="page-header_edit" type="primary" /> : '';
        const moveButton = page._links.move ? <ActionButton onClick={this.onMoveClick} i18nKey="page-header_move" type="primary" /> : '';
        const deleteButton = page._links.delete ? <ActionButton onClick={ this.onDeleteClick } i18nKey="page-header_delete" type="primary" /> : '';
        const restoreButton = page._links.restore ? <ActionButton onClick={this.onRestoreClick} i18nKey="page-header_restore" type="primary" /> : '';
        const createForm = <PageNameForm show={ this.state.showCreateForm } onOk={ this.onOkCreate } onAbortClick={ this.onAbortCreateClick } labelPrefix="create" directory={ wiki.directory } />
        const moveForm = <PageNameForm show={ this.state.showMoveForm } onOk={ this.onOkMoveClick } onAbortClick={ this.onAbortMoveClick } labelPrefix="move" directory={ wiki.directory } initialValue={ pathWithoutRoot } />
        const deleteConfirmModal = <ConfirmModal show={ this.state.showDeleteConfirm } onOk={ onDelete } onAbortClick={ this.onAbortDeleteClick } labelPrefix="delete" />

        return (
            <div className={classes.header}>
                <h1>{ page.path }</h1>
                <div className={classNames(classes.actions, "row")}>
                    <div className="col-xs-9">
                        {homeButton}
                        {createButton}
                        {moveButton}
                        {pagesButton}
                        {historyButton}
                        {edit}
                        {deleteButton}
                        {restoreButton}
                    </div>
                    <div className="col-xs-3">
                        <SearchBar search={search}/>
                    </div>
                </div>
                {createForm}
                {moveForm}
                {deleteConfirmModal}
            </div>
        );
    }

}

export default withRouter(injectSheet(styles)(PageHeader));
