import {Component, Input, EventEmitter, ViewEncapsulation, Output} from '@angular/core';
import 'rxjs/add/operator/map'
import { Observable } from 'rxjs/Observable';
import { BrowserModule } from '@angular/platform-browser';
import {Translation} from "../../common/translation";
import {Toast} from "../../common/ui/toast";
import {ArchiveRestore, RestoreResult, ArchiveSearch,Node} from "../../common/rest/data-object";
import {RestConnectorService} from "../../common/rest/services/rest-connector.service";
import {OptionItem} from "../../common/ui/actionbar/option-item";
import {ConfigurationService} from "../../common/services/configuration.service";
import {SessionStorageService} from "../../common/services/session-storage.service";
import {ActivatedRoute} from "@angular/router";
import {ListItem} from "../../common/ui/list-item";
import {TranslateService} from "@ngx-translate/core";

@Component({
  selector: 'node-list',
  templateUrl: 'node-list.component.html',
  styleUrls: ['node-list.component.scss']
  /*encapsulation:ViewEncapsulation.None*/
})
export class NodeListComponent {
  @Input() isInsideWorkspace = false;
  @Input() searchLabel : string;
  @Input() parent : any;
  @Input() set searchWorkspace(query : string){
    if(query && query.trim()) {
      this.currentQuery = query;
      this.hasSearched=true;
      this.doReload();
    }
    else{
      this.searchAll();
    }
  }
  public list : Node[];
  private _columns : ListItem[];
  @Input() set columns(columns : ListItem[]){
    this._columns=columns;
    if(this._columns && this._columns.length)
      this.sortBy=this._columns[0].name;
  };
  @Input() options : OptionItem[];
  private sortBy : string;
  private sortAscending=true;
  @Input() set reload(reload:Boolean){
    if(reload)
      this.doReload();
}
  @Output() onSelectionChanged = new EventEmitter();
  public hasSearched = false;
  public selected:Node[] = [];
    @Input() fullscreenLoading=false;
    // current list loading offset
    private offset = 0;

    // the current node which has an overlay menu open
    public currentMore : Node;
    public isLoading=false;
    public query : string;
    private currentQuery : string;
	  constructor(private connector : RestConnectorService,
                private translate : TranslateService,
                private config : ConfigurationService,
                private storage : SessionStorageService,
                private route : ActivatedRoute,
                private toast: Toast) {
        // http://plnkr.co/edit/btpW3l0jr5beJVjohy1Q?p=preview
        Translation.initialize(translate,this.config,this.storage,this.route).subscribe(()=>{});

        /*
        let restoreResult=new ArchiveRestore();
        let r1=new RestoreResult();
        r1.name="Duplicate";r1.restoreStatus=RecycleRestoreComponent.STATUS_DUPLICATENAME;
        let r2=new RestoreResult();
        r2.name="Missing";r2.restoreStatus=RecycleRestoreComponent.STATUS_PARENT_FOLDER_MISSING;
        restoreResult.results=[r1,r2];
        RecycleRestoreComponent.prepareResults(this.translate,restoreResult);
        this.restoreResult=restoreResult;
        */

    }

    private setSorting(data:any){
      this.sortBy=data.sortBy;
      this.sortAscending=data.sortAscending;
      this.doReload();
    }
    private loadMore() : void{
      console.log("scrolled");
      if(this.isLoading) {
          return;
        }
      this.search(false);
    }
    public searchField() : void{
        this.currentQuery=this.query;
        this.offset=0;
        this.list=null;
        if(this.query=="")
          this.searchAll();
        else
          this.search(true);
    }

    private onSelection(node : Node[]){
      this.selected=node;
      this.onSelectionChanged.emit(node);
    }



    private doReload() : void{
      this.offset=0;
      this.list=null;
      this.search(this.hasSearched);
    }
    private searchAll() : void{
        this.hasSearched=false;
        this.currentQuery="*";
        this.offset=0;
        this.doReload();
    }
    private redo() : void{
        this.query="";
        this.searchAll();
    }


	private search(searched : boolean) : void{
	if(this.isLoading){
	  setTimeout(()=>this.search(searched),10);
	  return;
    }
    this.isLoading=true;
    console.log('search '+this.currentQuery);

    this.parent.loadData(this.currentQuery,this.offset,this.sortBy,this.sortAscending)
            .subscribe(
				(data:ArchiveSearch) => this.display(data,searched),
              (error:any) => this.handleErrors(error),
                () => console.log('Get all Items complete'));
	}
    private isSelected(node : Node){
        return this.selected.indexOf(node)!=-1;
    }

    private display(data : ArchiveSearch,searched : boolean){
      console.log(data);
      let list=data.nodes;
        if(this.offset!=0){
          this.list=this.list.concat(list);
        }
        else{
            this.list=list;
            this.selected=[];
            if(this.list.length==0)
                this.list=null;
        }
        this.offset+=this.connector.numberPerRequest;


        this.hasSearched=searched;
        this.isLoading=false;

    }

  private handleErrors(error: any) {
    this.toast.error(error);
      this.fullscreenLoading=false;
  }

}
