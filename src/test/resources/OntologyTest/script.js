window.initVFB();
G.setIdleTimeOut(-1);
GEPPETTO.SceneController.setWireframe(false);
G.setOnSelectionOptions({unselected_transparent:false});

Model.getDatasources()[0].fetchVariable('VFB_00000001');
Model.getDatasources()[0].fetchVariable('FBbt_00100219');
Instances.getInstance("VFB_00000001.VFB_00000001_meta");
Instances.getInstance("FBbt_00100219.FBbt_00100219_meta");
resolve3D('VFB_00000001');
resolve3D('FBbt_00100219');

