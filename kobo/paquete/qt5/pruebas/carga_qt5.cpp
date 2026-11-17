// Prueba con Qt 5 de verdad: lo mismo que hace QImageReader (QFactoryLoader con el IID de imageformats).
#include <QtCore/QCoreApplication>
#include <QtCore/QPluginLoader>
#include <QtCore/QJsonDocument>
#include <QtCore/QJsonObject>
#include <QtCore/QMultiMap>
#include <QtCore/QDebug>
#include <cstdio>
#include <unistd.h>
class QFactoryLoader : public QObject {  // (privada de QtCore, exportada; misma firma que en qfactoryloader_p.h de 5.15)
public:
    explicit QFactoryLoader(const char *iid, const QString &suffix = QString(), Qt::CaseSensitivity = Qt::CaseSensitive);
    QList<QJsonObject> metaData() const;
    QObject *instance(int index) const;
    QMultiMap<int, QString> keyMap() const;
};
int main(int argc, char **argv) {
    QCoreApplication app(argc, argv);
    QCoreApplication::setLibraryPaths(QStringList() << QString::fromLocal8Bit(argv[1]));
    printf("Qt en ejecución: %s\n", qVersion());
    for (int i = 2; i < argc; i++) {
        QPluginLoader pl(QString::fromLocal8Bit(argv[i]));
        printf("QPluginLoader(%s).metaData() = %s\n", argv[i], QJsonDocument(pl.metaData()).toJson(QJsonDocument::Compact).constData());
    }
    QFactoryLoader *l = new QFactoryLoader("org.qt-project.Qt.QImageIOHandlerFactoryInterface", QStringLiteral("/imageformats"), Qt::CaseInsensitive);
    QMultiMap<int, QString> km = l->keyMap();
    for (auto it = km.constBegin(); it != km.constEnd(); ++it) printf("keyMap: %d -> %s\n", it.key(), qPrintable(it.value()));
    const int n = km.keys().size();
    for (int i = 0; i < n; i++) { QObject *o = l->instance(i); printf("instance(%d) = %p\n", i, (void*)o); }
    fflush(stdout);
    sleep(2);
    return 0;
}
