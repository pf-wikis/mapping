import lxml.etree as ET
from io import StringIO
from qgis.core import QgsProject # type: ignore


def classFactory(iface):
    return StableProjectFile(iface)


class StableProjectFile:
    def __init__(self, iface):
        self.iface = iface

    def normalize_xml(self):
        project = QgsProject.instance()

        #  We don't do anything with qgz projects
        if project.isZipped():
            return

        # Anything but local files will return a pointer to the storage
        if project.projectStorage():
            return

        fn = project.fileName()
        with open(fn, 'r', encoding='utf-8') as file:
            tree = ET.parse(file)

        #remove name of last user
        tree.getroot().attrib.pop('saveUserFull', None)
        tree.getroot().attrib.pop('saveUser', None)
        
        #sort individual-layer-settings
        ilSettings = tree.getroot().find('snapping-settings/individual-layer-settings')
        ilSettings[:]=sorted(ilSettings, key=lambda e: e.get('id'))
        
        #sort lineSymbol values
        for tag in tree.iter():
            if (
                tag.tag == 'Option'
                and tag.get('name')=='lineSymbol'
                and tag.get('type')=='QString'
                and tag.get('value')
                and tag.get('value').startswith('<')
            ):
                value = tag.get('value')
                valueT = ET.fromstring(value)

                #sort options
                options = valueT.find('data_defined_properties/Option')
                options[:]=sorted(options, key=lambda e: (e.get('type') or 'zzz') + (e.get('id') or ''))

                result = ET.tostring(valueT, method='c14n')
                tag.set('value', result)


        tree.write_c14n(fn)

    def initGui(self):
        self.project_saved_connection = \
            QgsProject.instance().projectSaved.connect(self.normalize_xml)

    def unload(self):
        p = QgsProject.instance()
        if p and self.project_saved_connection:
            p.projectSaved.disconnect(self.project_saved_connection)
