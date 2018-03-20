//@flow
import React from 'react';
import PageContent from './PageContent';
import PageHeader from './PageHeader';
import PageFooter from './PageFooter';

type Props = {
    page: any,
    wiki: any,
    pagesLink: string,
    historyLink: string,
    onDelete: () => void,
    onHome: () => void,
    onMove: () => void,
    onRestore: () => void
};

class PageViewer extends React.Component<Props> {

    render() {
        const { page, wiki, onDelete, onHome, onMove, pagesLink, historyLink, onRestore } = this.props;

        return (
            <div>
                <PageHeader page={page} wiki={wiki} pagesLink={pagesLink} historyLink={historyLink}
                            onDeleteClick={onDelete} onHomeClick={onHome} onOkMoveClick={onMove} onRestoreClick={onRestore}/>
                <PageContent page={page} />
                <PageFooter page={page} />
            </div>
        );
    }

}

export default PageViewer;
